package com.app.sumpyo_be.domain.talk.service;

import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.reading.dto.projection.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import com.app.sumpyo_be.domain.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService;
import com.app.sumpyo_be.domain.talk.entity.TalkPost;
import com.app.sumpyo_be.domain.talk.repository.TalkPostRepository;
import com.app.sumpyo_be.domain.talk.suggestion.LlmSuggestionGenerator;
import com.app.sumpyo_be.domain.talk.suggestion.RuleBasedSuggestionGenerator;
import com.app.sumpyo_be.domain.talk.suggestion.SuggestionContext;
import com.app.sumpyo_be.global.ai.LlmClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천 답변. 재료(대상 강의실·지금 수업·CO2)를 모아 LLM 또는 규칙 기반으로 만든다.
 * - 글 작성 커밋 후 비동기(TalkSuggestionListener): LLM, 실패·타임아웃이면 규칙 기반 → 저장
 * - 상세 조회: LLM 을 부르지 않는다. 30분 이내 저장값은 그대로, 아직 없으면 규칙 기반(저장 안 함, 비동기 결과가 덮어씀),
 *   30분 지났으면 규칙 기반으로 다시 만들어 저장 (수업 중·CO2 같은 정보가 바뀌었을 수 있어서)
 */
@Slf4j
@Service
public class TalkSuggestionService {

  /** 저장된 추천을 그대로 쓰는 기간 */
  static final Duration FRESH_FOR = Duration.ofMinutes(30);

  /** 본문의 "617호" */
  private static final Pattern ROOM_IN_CONTENT = Pattern.compile("(\\d{3,4})호");

  private final RuleBasedSuggestionGenerator rules;
  /** LLM 설정이 없으면 null */
  private final LlmSuggestionGenerator llm;
  private final JsonMapper jsonMapper;
  private final TalkPostRepository postRepository;
  private final RoomRepository roomRepository;
  private final ClassScheduleService scheduleService;
  private final SensorReadingRepository readingRepository;
  private final Clock clock;

  public TalkSuggestionService(RuleBasedSuggestionGenerator rules, ObjectProvider<LlmClient> llmClient,
      JsonMapper jsonMapper, TalkPostRepository postRepository, RoomRepository roomRepository,
      ClassScheduleService scheduleService, SensorReadingRepository readingRepository, Clock clock) {
    LlmClient client = llmClient.getIfAvailable();
    this.rules = rules;
    this.llm = client == null ? null : new LlmSuggestionGenerator(client, jsonMapper);
    this.jsonMapper = jsonMapper;
    this.postRepository = postRepository;
    this.roomRepository = roomRepository;
    this.scheduleService = scheduleService;
    this.readingRepository = readingRepository;
    this.clock = clock;
    log.info("캠퍼스톡 추천 답변: {}", llm == null ? "규칙 기반 (LLM 설정 없음)" : "LLM, 실패하면 규칙 기반");
  }

  /** 비동기(작성 커밋 후). LLM → 실패·타임아웃·설정 없음이면 규칙 기반. 결과와 생성 시각을 저장한다 */
  public void generateAndSave(Long postId) {
    Optional<TalkPost> found = postRepository.findDetailById(postId);
    if (found.isEmpty()) {
      return;
    }
    SuggestionContext ctx = context(found.get());
    List<String> suggestions = Optional.ofNullable(llm).flatMap(l -> l.generate(ctx))
        .orElseGet(() -> rules.generate(ctx));
    save(postId, suggestions);
  }

  /**
   * 상세 조회 (보낸 사람이 아닌 사람). LLM 은 부르지 않는다.
   * post 의 building·room 은 이미 로딩된 상태여야 한다
   */
  public List<String> forDetail(TalkPost post) {
    if (post.getSuggestions() == null) {
      return rules.generate(context(post));   // 비동기 진행 중. 저장은 비동기 결과가 한다
    }
    LocalDateTime generatedAt = post.getSuggestionsGeneratedAt();
    if (generatedAt != null && generatedAt.isAfter(LocalDateTime.now(clock).minus(FRESH_FOR))) {
      return jsonMapper.readValue(post.getSuggestions(), new TypeReference<List<String>>() {});
    }
    List<String> regenerated = rules.generate(context(post));
    save(post.getId(), regenerated);
    return regenerated;
  }

  private void save(Long postId, List<String> suggestions) {
    postRepository.saveSuggestions(postId, jsonMapper.writeValueAsString(suggestions), LocalDateTime.now(clock));
  }

  private SuggestionContext context(TalkPost post) {
    LocalDateTime now = LocalDateTime.now(clock);
    Room room = post.getRoom() != null ? post.getRoom() : roomInContent(post).orElse(null);

    boolean inClass = false;
    String courseName = null;
    ClassSchedule current = null;
    Integer co2 = null;
    if (room != null) {
      List<ClassSchedule> today = scheduleService.classesOn(room.getId(), now.toLocalDate());   // 휴강 제외
      current = ClassScheduleService.currentAndNext(today, now.toLocalTime()).current();
      inClass = current != null;
      courseName = inClass ? current.getCourseName() : null;
      if (room.getSensorLevel().hasCo2()) {
        co2 = readingRepository.findLatestSince(List.of(room.getId()), now.minus(AirQualityJudge.FRESH_WINDOW))
            .stream().findFirst().map(ReadingSnapshot::getCo2).orElse(null);
      }
    }
    return new SuggestionContext(post.getBuilding().getName(), post.getFloor(),
        post.getContent(), room == null ? null : room.getNumber(), inClass, courseName,
        inClass ? current.getEndTime() : null, co2);
  }

  /** 본문에서 처음 나오는 "617호" 를 같은 건물에서 찾는다 */
  private Optional<Room> roomInContent(TalkPost post) {
    Matcher m = ROOM_IN_CONTENT.matcher(post.getContent());
    if (!m.find()) {
      return Optional.empty();
    }
    return roomRepository.findLectureWithBuildingByBuildingIdAndNumber(post.getBuilding().getId(), m.group(1));
  }
}
