package com.app.sumpyo_be.domain.simulator;

import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.repository.ActionRepository;
import com.app.sumpyo_be.domain.reading.dto.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.dto.SensorPayload;
import com.app.sumpyo_be.domain.reading.entity.SensorReading;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.entity.SensorLevel;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import com.app.sumpyo_be.domain.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService;
import com.app.sumpyo_be.domain.simulator.SimulationModel.Conditions;
import com.app.sumpyo_be.domain.simulator.SimulationModel.State;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * sensor_level = SIM 인 방마다 1분에 한 번 sensor_reading 을 넣는다. 식은 SimulationModel 참고.
 * 끄려면 sumpyo.simulator.enabled=false (환경변수 SIMULATOR_ENABLED=false).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sumpyo.simulator", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SensorSimulator {

  /** 앱 재시작 시 이보다 오래된 마지막 측정은 이어받지 않고 새로 시작 */
  private static final Duration RESUME_WINDOW = Duration.ofDays(1);

  private final RoomRepository roomRepository;
  private final SensorReadingRepository readingRepository;
  private final ClassScheduleService classScheduleService;
  private final ActionRepository actionRepository;
  private final Clock clock;

  /** 방별 소수 상태. 재시작하면 DB 의 마지막 측정값에서 이어간다 */
  private final Map<Long, State> states = new ConcurrentHashMap<>();
  // RandomGenerator.getDefault() 는 jdk.random 모듈(L32X64MixRandom)이 필요한데 운영 이미지(temurin 21-jre)에 없다.
  // SplittableRandom 은 java.base 라 어느 JRE 에서도 동작한다. 스케줄러 한 스레드에서만 써서 동기화 불필요.
  private final RandomGenerator random = new SplittableRandom();

  @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
  @Transactional
  public void tick() {
    simulate(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
  }

  void simulate(LocalDateTime now) {
    List<Room> rooms = roomRepository.findBySensorLevel(SensorLevel.SIM);
    if (rooms.isEmpty()) {
      return;
    }
    List<Long> roomIds = rooms.stream().map(Room::getId).toList();
    Map<Long, List<ClassSchedule>> classes = classScheduleService.classesOn(roomIds, now.toLocalDate());
    Set<Long> ventilating = new HashSet<>(actionRepository.findRoomIdsByStatus(roomIds, ActionStatus.IN_PROGRESS));
    Map<Long, ReadingSnapshot> resume = lastReadingsForNewRooms(roomIds, now);

    List<SensorReading> readings = new ArrayList<>(rooms.size());
    for (Room room : rooms) {
      State prev = states.computeIfAbsent(room.getId(), id -> resumeOrInitial(resume.get(id)));
      Conditions c = SimulationModel.conditions(room.getId(), classes.getOrDefault(room.getId(), List.of()), now,
          ventilating.contains(room.getId()));
      State next = SimulationModel.next(prev, c);
      states.put(room.getId(), next);
      int[] v = SimulationModel.observe(next, random);
      readings.add(SensorReading.of(room, new SensorPayload(v[0], v[1], v[2]), now));
    }
    readingRepository.saveAll(readings);
    log.debug("시뮬레이터: {}개 방 측정값 저장", readings.size());
  }

  /** 메모리 상태가 없는 방(앱 시작 직후)만 DB 에서 마지막 측정을 가져온다 */
  private Map<Long, ReadingSnapshot> lastReadingsForNewRooms(List<Long> roomIds, LocalDateTime now) {
    List<Long> missing = roomIds.stream().filter(id -> !states.containsKey(id)).toList();
    if (missing.isEmpty()) {
      return Map.of();
    }
    return readingRepository.findLatestSince(missing, now.minus(RESUME_WINDOW)).stream()
        .collect(Collectors.toMap(ReadingSnapshot::getRoomId, Function.identity(), (a, b) -> a));
  }

  private State resumeOrInitial(ReadingSnapshot last) {
    if (last == null || last.getCo2() == null) {
      return SimulationModel.initial(random);
    }
    return new State(last.getCo2(),
        last.getTemp() != null ? last.getTemp() : SimulationModel.BASE_TEMP,
        last.getHumidity() != null ? last.getHumidity() : SimulationModel.BASE_HUMIDITY);
  }
}
