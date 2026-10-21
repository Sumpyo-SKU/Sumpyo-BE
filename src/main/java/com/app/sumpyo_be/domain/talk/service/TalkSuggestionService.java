package com.app.sumpyo_be.domain.talk.service;

import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import com.app.sumpyo_be.domain.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService;
import com.app.sumpyo_be.domain.talk.entity.TalkPost;
import com.app.sumpyo_be.domain.talk.suggestion.SuggestionContext;
import com.app.sumpyo_be.domain.talk.suggestion.SuggestionGenerator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 추천 답변 재료(대상 강의실·지금 수업·CO2)를 모아 SuggestionGenerator 에 넘긴다 */
@Service
@RequiredArgsConstructor
public class TalkSuggestionService {

  /** 본문의 "617호" */
  private static final Pattern ROOM_IN_CONTENT = Pattern.compile("(\\d{3,4})호");

  private final SuggestionGenerator generator;
  private final RoomRepository roomRepository;
  private final ClassScheduleService scheduleService;
  private final SensorReadingRepository readingRepository;
  private final Clock clock;

  /** post 의 building·room 은 이미 로딩된 상태여야 한다 */
  public List<String> suggest(TalkPost post) {
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
    return generator.generate(new SuggestionContext(post.getBuilding().getName(), post.getFloor(),
        post.getContent(), room == null ? null : room.getNumber(), inClass, courseName,
        inClass ? current.getEndTime() : null, co2));
  }

  /** 본문에서 처음 나오는 "617호" 를 같은 건물에서 찾는다 */
  private Optional<Room> roomInContent(TalkPost post) {
    Matcher m = ROOM_IN_CONTENT.matcher(post.getContent());
    if (!m.find()) {
      return Optional.empty();
    }
    return roomRepository.findWithBuildingByBuildingIdAndNumber(post.getBuilding().getId(), m.group(1));
  }
}
