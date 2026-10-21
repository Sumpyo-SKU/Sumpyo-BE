package com.app.sumpyo_be.domain.talk.service;

import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * 추천 답변. 재료(대상 강의실·지금 수업·CO2)를 모아 LLM 또는 규칙 기반으로 만든다.
 * LLM 응답이 느려서(무료 티어 3~17초) 글을 쓰자마자 백그라운드에서 만들어 저장해 두고(prepare),
 * 상세를 열었는데 아직 없으면 최대 3초만 기다린 뒤 규칙 기반을 보여준다(suggestNow).
 * 규칙 기반 결과는 저장하지 않으므로, LLM 결과가 늦게 와도 저장되어 다음에 열 때 보인다.
 */
@Slf4j
@Service
public class TalkSuggestionService {

  /** 상세 화면에서 LLM 을 기다리는 최대 시간 */
  static final Duration DETAIL_WAIT = Duration.ofSeconds(3);

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
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  /** 진행 중인 LLM 호출 (글 ID 별). 작성 직후 백그라운드와 상세가 같은 호출을 기다린다 */
  private final Map<Long, CompletableFuture<Optional<List<String>>>> inFlight = new ConcurrentHashMap<>();
  Duration detailWait = DETAIL_WAIT;   // 테스트에서 줄인다

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
    log.info("캠퍼스톡 추천 답변: {}", llm == null ? "규칙 기반 (LLM 설정 없음)" : "LLM, 늦거나 실패하면 규칙 기반");
  }

  /** 글 작성(커밋) 직후. LLM 이 있으면 백그라운드에서 만들어 저장한다 */
  public void prepare(Long postId) {
    if (llm != null) {
      start(postId);
    }
  }

  /** 상세에서 저장된 추천이 없을 때. post 의 building·room 은 이미 로딩된 상태여야 한다 */
  public List<String> suggestNow(TalkPost post) {
    if (llm != null) {
      try {
        Optional<List<String>> generated = start(post.getId()).get(detailWait.toMillis(), TimeUnit.MILLISECONDS);
        if (generated.isPresent()) {
          return generated.get();
        }
      } catch (TimeoutException e) {
        // 호출은 계속 진행되고, 끝나면 저장된다
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        // start 의 작업은 예외를 밖으로 던지지 않는다
      }
    }
    return rules.generate(context(post));
  }

  /** 같은 글에 이미 진행 중인 호출이 있으면 그것을 돌려준다 */
  private CompletableFuture<Optional<List<String>>> start(Long postId) {
    CompletableFuture<Optional<List<String>>> future = new CompletableFuture<>();
    CompletableFuture<Optional<List<String>>> running = inFlight.putIfAbsent(postId, future);
    if (running != null) {
      return running;
    }
    executor.execute(() -> {
      try {
        future.complete(generateAndSave(postId));
      } catch (RuntimeException e) {
        log.warn("추천 답변 생성 실패({})", e.getClass().getSimpleName());
        future.complete(Optional.empty());
      } finally {
        inFlight.remove(postId, future);
      }
    });
    return future;
  }

  private Optional<List<String>> generateAndSave(Long postId) {
    Optional<TalkPost> post = postRepository.findDetailById(postId);
    if (post.isEmpty() || post.get().getSuggestions() != null) {
      return Optional.empty();
    }
    Optional<List<String>> generated = llm.generate(context(post.get()));
    generated.ifPresent(s -> postRepository.saveSuggestionsIfAbsent(postId, jsonMapper.writeValueAsString(s)));
    return generated;
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
