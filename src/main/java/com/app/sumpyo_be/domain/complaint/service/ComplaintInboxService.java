package com.app.sumpyo_be.domain.complaint.service;

import com.app.sumpyo_be.domain.action.repository.ActionRepository;
import com.app.sumpyo_be.domain.action.service.ComplaintActionAssembler;
import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.domain.complaint.dto.response.ComplaintDetailResponse;
import com.app.sumpyo_be.domain.complaint.dto.response.ComplaintListResponse;
import com.app.sumpyo_be.domain.complaint.dto.response.RepeatedComplaintResponse;
import com.app.sumpyo_be.domain.building.entity.Building;
import com.app.sumpyo_be.domain.building.repository.BuildingRepository;
import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintStatus;
import com.app.sumpyo_be.domain.complaint.repository.ComplaintRepository;
import com.app.sumpyo_be.domain.complaint.repository.ComplaintSearchTerms;
import com.app.sumpyo_be.domain.complaint.repository.ComplaintSpecs;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.member.service.ThresholdService;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.room.entity.Room;
import com.app.sumpyo_be.domain.room.repository.RoomRepository;
import com.app.sumpyo_be.global.common.TimeIdCursor;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 민원 접수함 (권한 W, A): 목록, 이번 주 반복 민원, 상세 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplaintInboxService {

  static final int PREVIEW_LENGTH = 50;
  /** 이번 주 이 건수 이상이면 반복 민원 카드 */
  static final int REPEATED_FROM = 5;
  private static final Pattern WEEK = Pattern.compile("(\\d{4})-W(\\d{2})");

  private final MemberAccess memberAccess;
  private final ComplaintRepository complaintRepository;
  private final RoomRepository roomRepository;
  private final BuildingRepository buildingRepository;
  private final SensorReadingRepository readingRepository;
  private final ActionRepository actionRepository;
  private final ComplaintActionAssembler actionAssembler;
  private final ThresholdService thresholdService;
  private final Clock clock;

  /** 검색어 정리 (건물 이름은 숫자와 같이 쓰면 떼어내므로 이름 목록이 필요). 검색어가 없으면 DB 를 안 읽는다 */
  private List<ComplaintSearchTerms.Term> searchTerms(String q) {
    if (q == null || q.isBlank()) {
      return List.of();
    }
    return ComplaintSearchTerms.parse(q, buildingRepository.findAll().stream().map(Building::getName).toList());
  }

  /**
   * 접수 최신순 (created_at DESC, id DESC) 무한 스크롤. size + 1 개를 읽어 다음이 있는지 판단한다.
   * counts 는 status·cursor 를 뺀 같은 필터 기준.
   */
  public ComplaintListResponse list(ComplaintStatus status, Long buildingId, Long roomId, String q, String cursor,
      int size) {
    memberAccess.require(Role.WORKER, Role.ADMIN);
    TimeIdCursor after = TimeIdCursor.decode(cursor);
    Specification<Complaint> base = Specification.where(ComplaintSpecs.inBuilding(buildingId))
        .and(ComplaintSpecs.inRoom(roomId)).and(ComplaintSpecs.search(searchTerms(q)));

    List<Complaint> fetched = complaintRepository.findAll(
        base.and(ComplaintSpecs.hasStatus(status)).and(ComplaintSpecs.before(after)),
        PageRequest.of(0, size + 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).getContent();
    boolean hasNext = fetched.size() > size;
    List<Complaint> page = hasNext ? fetched.subList(0, size) : fetched;

    long received = complaintRepository.count(base.and(ComplaintSpecs.hasStatus(ComplaintStatus.RECEIVED)));
    long inProgress = complaintRepository.count(base.and(ComplaintSpecs.hasStatus(ComplaintStatus.IN_PROGRESS)));
    long done = complaintRepository.count(base.and(ComplaintSpecs.hasStatus(ComplaintStatus.DONE)));
    Complaint last = hasNext ? page.getLast() : null;
    return ComplaintListResponse.builder()
        .counts(new ComplaintListResponse.Counts(received + inProgress + done, received, inProgress, done))
        .content(page.stream().map(c -> ComplaintListResponse.Item.builder()
            .complaintId(c.getId())
            .room(roomName(c.getRoom()))
            .categoryLabels(labels(c))
            .preview(preview(c.getContent()))
            .status(c.getStatus())
            .createdAt(c.getCreatedAt())
            .build()).toList())
        .nextCursor(last == null ? null : new TimeIdCursor(last.getCreatedAt(), last.getId()).encode())
        .hasNext(hasNext)
        .build();
  }

  /**
   * 그 주(월~일, 기본 이번 주) 민원 5건 이상인 방, 많은 순.
   * overThresholdDays = 그 주 중 방 최고 CO2 가 조회자 기준 이상인 날 수 (CO2 센서 없는 방은 null).
   * @param week "2026-W43" (ISO 주). null 이면 이번 주
   */
  public List<RepeatedComplaintResponse> repeated(Long buildingId, String week) {
    memberAccess.require(Role.WORKER, Role.ADMIN);
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDate monday = week == null || week.isBlank() ? now.toLocalDate().with(DayOfWeek.MONDAY) : parseWeek(week);
    LocalDateTime from = monday.atStartOfDay();
    LocalDateTime to = from.plusDays(7);
    int threshold = thresholdService.current().co2();

    List<Object[]> rows = complaintRepository.countByRoomBetween(from, to, REPEATED_FROM);
    Map<Long, Room> rooms = roomRepository.findAllById(rows.stream().map(r -> (Long) r[0]).toList()).stream()
        .collect(Collectors.toMap(Room::getId, Function.identity()));
    List<RepeatedComplaintResponse> result = new ArrayList<>();
    for (Object[] row : rows) {
      Room room = rooms.get((Long) row[0]);
      if (room == null || (buildingId != null && !room.getBuilding().getId().equals(buildingId))) {
        continue;
      }
      long count = ((Number) row[1]).longValue();
      Integer overDays = null;
      String message = "이번 주 " + count + "건";
      if (room.getSensorLevel().hasCo2()) {
        List<LocalDate> over = overThresholdDates(room.getId(), from, to, threshold);
        overDays = over.size();
        long weekdaysOver = over.stream().filter(d -> d.getDayOfWeek().getValue() <= 5).count();
        int pastWeekdays = pastWeekdays(monday, now.toLocalDate());
        if (overDays > 0) {
          message += " · 센서도 " + (pastWeekdays > 0 && weekdaysOver >= pastWeekdays ? "매일" : overDays + "일")
              + " " + String.format(Locale.KOREA, "%,d", threshold) + "ppm 초과";
        }
      }
      result.add(new RepeatedComplaintResponse(room.getId(), roomName(room), count, overDays, message));
    }
    return result;
  }

  public ComplaintDetailResponse detail(Long complaintId) {
    memberAccess.require(Role.WORKER, Role.ADMIN);
    Complaint c = complaintRepository.findDetailById(complaintId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.COMPLAINT_NOT_FOUND));
    return ComplaintDetailResponse.builder()
        .complaintId(c.getId())
        .room(roomName(c.getRoom()))
        .roomId(c.getRoom().getId())
        .categoryLabels(labels(c))
        .content(c.getContent())
        .status(c.getStatus())
        .createdAt(c.getCreatedAt())
        .snapshot(snapshot(c, thresholdService.current()))
        .action(actionRepository.findTopByComplaintIdOrderByCreatedAtDescIdDesc(c.getId())
            .map(actionAssembler::toResponse).orElse(null))
        .build();
  }

  /** 접수 시점 측정값을 조회자 기준값으로 판정 (AirQualityJudge). 측정이 하나도 없었으면 null */
  static ComplaintDetailResponse.Snapshot snapshot(Complaint c, Thresholds t) {
    Integer co2 = c.getCo2Snapshot();
    Integer humidity = c.getHumiditySnapshot();
    Double temp = c.getTempSnapshot() == null ? null : c.getTempSnapshot().doubleValue();
    if (co2 == null && humidity == null && temp == null) {
      return null;
    }
    Integer tempRounded = temp == null ? null : (int) Math.round(temp);
    Integer score = AirQualityJudge.comfortScore(co2, tempRounded, humidity, t);
    return ComplaintDetailResponse.Snapshot.builder()
        .co2(co2)
        .co2Level(AirQualityJudge.co2Level(co2, t.co2()))
        .co2OverBy(AirQualityJudge.co2OverBy(co2, t.co2()))
        .comfortScore(score)
        .comfortLevel(AirQualityJudge.comfortLevel(score))
        .temp(temp)
        .tempLabel(AirQualityJudge.tempLabel(tempRounded, t.tempMin(), t.tempMax()))
        .humidity(humidity)
        .humidityLabel(AirQualityJudge.humidityLabel(humidity, t.humidityMin(), t.humidityMax()))
        .build();
  }

  /** 그 주 중 최고 CO2 가 기준 이상인 날 */
  private List<LocalDate> overThresholdDates(Long roomId, LocalDateTime from, LocalDateTime to, int threshold) {
    List<LocalDate> dates = new ArrayList<>();
    for (Object[] row : readingRepository.findDailyMaxCo2(roomId, from, to)) {
      if (row[1] != null && ((Number) row[1]).intValue() >= threshold) {
        dates.add(row[0] instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) row[0]);
      }
    }
    return dates;
  }

  /** 그 주의 지난 평일 수 (오늘 포함). 지난 주면 5 */
  static int pastWeekdays(LocalDate monday, LocalDate today) {
    if (today.isBefore(monday)) {
      return 0;
    }
    LocalDate last = today.isAfter(monday.plusDays(4)) ? monday.plusDays(4) : today;
    return (int) (last.toEpochDay() - monday.toEpochDay()) + 1;
  }

  static LocalDate parseWeek(String week) {
    Matcher m = WEEK.matcher(week.strip());
    if (!m.matches()) {
      throw new CustomException(GlobalErrorCode.INVALID_WEEK);
    }
    int year = Integer.parseInt(m.group(1));
    int w = Integer.parseInt(m.group(2));
    LocalDate anyDay = LocalDate.of(year, 1, 4);   // 1월 4일은 항상 그해 첫 ISO 주
    if (w < 1 || w > anyDay.range(IsoFields.WEEK_OF_WEEK_BASED_YEAR).getMaximum()) {
      throw new CustomException(GlobalErrorCode.INVALID_WEEK);
    }
    return anyDay.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, w).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  static String roomName(Room room) {
    return room.getBuilding().getName() + " " + room.getNumber() + "호";
  }

  static List<String> labels(Complaint c) {
    return c.getCategoryList().stream().map(ComplaintCategory::getLabel).toList();
  }

  static String preview(String content) {
    if (content == null) {
      return null;
    }
    return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "…";
  }
}
