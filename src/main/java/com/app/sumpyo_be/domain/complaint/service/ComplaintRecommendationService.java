package com.app.sumpyo_be.domain.complaint.service;

import com.app.sumpyo_be.domain.action.dto.response.ComplaintActionResponse;
import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.ActionStatus;
import com.app.sumpyo_be.domain.action.entity.Metric;
import com.app.sumpyo_be.domain.action.entity.PlanSource;
import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.action.entity.Recommend;
import com.app.sumpyo_be.domain.action.entity.StepType;
import com.app.sumpyo_be.domain.action.repository.ActionRepository;
import com.app.sumpyo_be.domain.action.service.ComplaintActionAssembler;
import com.app.sumpyo_be.domain.action.service.PlanCodec;
import com.app.sumpyo_be.domain.air.AirQualityJudge;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintStatus;
import com.app.sumpyo_be.domain.complaint.recommend.LlmPlanner;
import com.app.sumpyo_be.domain.complaint.recommend.PlanCalculator;
import com.app.sumpyo_be.domain.complaint.recommend.PlanContext;
import com.app.sumpyo_be.domain.complaint.recommend.RawPlan;
import com.app.sumpyo_be.domain.complaint.recommend.RulePlanner;
import com.app.sumpyo_be.domain.complaint.repository.ComplaintRepository;
import com.app.sumpyo_be.domain.member.entity.Role;
import com.app.sumpyo_be.domain.member.service.MemberAccess;
import com.app.sumpyo_be.domain.member.service.ThresholdService;
import com.app.sumpyo_be.domain.outdoor.entity.OutdoorObservation;
import com.app.sumpyo_be.domain.outdoor.repository.OutdoorObservationRepository;
import com.app.sumpyo_be.domain.outdoor.service.RainForecast;
import com.app.sumpyo_be.domain.prediction.service.PredictionLookup;
import com.app.sumpyo_be.domain.reading.dto.projection.ReadingSnapshot;
import com.app.sumpyo_be.domain.reading.repository.SensorReadingRepository;
import com.app.sumpyo_be.domain.schedule.entity.ClassSchedule;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService;
import com.app.sumpyo_be.domain.schedule.service.ClassScheduleService.CurrentAndNext;
import com.app.sumpyo_be.global.exception.CustomException;
import com.app.sumpyo_be.global.exception.GlobalErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 민원 AI 추천 (POST /api/complaints/{id}/recommendation). LLM 이 조치를 고르고 숫자는 서버가 계산한다.
 * LLM 키 없음·타임아웃(10초)·검증 실패면 규칙 기반 (RulePlanner).
 * 지금 CO2 가 기준 이상이면 (다른 방법 포함) 창문 열기 단계가 반드시 들어간다: LLM 결과에 없으면 규칙 기반 창문 대안으로.
 * RECEIVED 민원만. 대기 중 추천이 있으면 그대로, [다른 방법](retry)이면 그 조치를 recommendForAction 과 같은 방식으로 재추천.
 * 조치 기준 재추천(recommendForAction)은 출처와 상관없이 대기 중 조치면 된다 (강의실 카드 [다른 방법]).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ComplaintRecommendationService {

  /** 바깥 공기 관측이 이보다 오래되면 쓰지 않는다 */
  static final Duration OUTDOOR_FRESH = Duration.ofHours(3);
  static final Duration RAIN_WITHIN = Duration.ofHours(1);
  /** "그대로 두면 30분 뒤" 예측 */
  static final Duration FORECAST_AHEAD = Duration.ofMinutes(30);
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final MemberAccess memberAccess;
  private final ComplaintRepository complaintRepository;
  private final ActionRepository actionRepository;
  private final SensorReadingRepository readingRepository;
  private final OutdoorObservationRepository outdoorRepository;
  private final RainForecast rainForecast;
  private final PredictionLookup predictionLookup;
  private final ClassScheduleService classScheduleService;
  private final ThresholdService thresholdService;
  private final LlmPlanner llmPlanner;
  private final RulePlanner rulePlanner;
  private final PlanCodec planCodec;
  private final ComplaintActionAssembler assembler;
  private final Clock clock;

  public ComplaintActionResponse recommend(Long complaintId, boolean retry) {
    memberAccess.require(Role.WORKER, Role.ADMIN);
    Complaint complaint = complaintRepository.findDetailById(complaintId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.COMPLAINT_NOT_FOUND));
    if (complaint.getStatus() != ComplaintStatus.RECEIVED) {
      throw new CustomException(GlobalErrorCode.INVALID_COMPLAINT_STATUS);
    }
    Optional<Action> waiting = actionRepository.findTopByComplaintIdAndStatusInOrderByCreatedAtDescIdDesc(
        complaintId, ActionStatus.WAITING);
    LocalDateTime now = LocalDateTime.now(clock);
    if (waiting.isPresent()) {
      // 대기 중 추천이 있으면 그대로, [다른 방법]이면 조치 기준 재추천 (POST /api/actions/{id}/recommendation 과 같은 처리)
      return assembler.toResponse(retry ? replan(waiting.get(), now) : waiting.get());
    }
    PlanContext ctx = context(complaint, null, now);
    Planned p = plan(ctx, metric(ctx));
    Action action = actionRepository.save(Action.complaintPlan(complaint.getRoom(), complaintId, p.recommend(),
        p.metric(), p.summary(), p.reason(), p.plan(), p.source(), ctx.co2(), p.expectedCo2After(), ctx.forecastCo2(),
        p.acTargetTemp(), now));
    return assembler.toResponse(action);
  }

  /**
   * [다른 방법] 조치 기준 재추천 (POST /api/actions/{actionId}/recommendation). 출처(예측·민원·직접 환기)와 상관없이
   * 대기 중(PENDING·CHECKED) 조치만, 아니면 409 G049. 같은 조치 행의 추천을 바꾸므로 민원 조치면 민원 상세에도 그대로 보인다.
   */
  public ComplaintActionResponse recommendForAction(Long actionId) {
    memberAccess.require(Role.WORKER, Role.ADMIN);
    Action action = actionRepository.findById(actionId)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.ACTION_NOT_FOUND));
    if (!action.getStatus().isWaiting()) {
      throw new CustomException(GlobalErrorCode.ACTION_NOT_REPLANNABLE);
    }
    return assembler.toResponse(replan(action, LocalDateTime.now(clock)));
  }

  /**
   * 대기 중 조치의 추천을 이전과 다른 새 추천으로 바꾼다. 민원 조치는 민원 내용·접수 때 값으로,
   * 민원 없는 조치는 강의실 상황(CO2 기준 초과·초과 예상)으로 추천 재료를 모은다. 체크리스트가 없던 조치면 처음 추천
   */
  private Action replan(Action action, LocalDateTime now) {
    PlanContext.Previous previous = action.hasPlan()
        ? new PlanContext.Previous(action.getSummary(), planCodec.read(action.getPlan())) : null;
    PlanContext ctx;
    Metric metric;
    if (action.getComplaintId() != null) {
      Complaint complaint = complaintRepository.findDetailById(action.getComplaintId())
          .orElseThrow(() -> new CustomException(GlobalErrorCode.COMPLAINT_NOT_FOUND));
      ctx = context(complaint, previous, now);
      metric = metric(ctx);
    } else {
      ctx = roomContext(action, previous, now);
      metric = Metric.CO2;
    }
    Planned p = plan(ctx, metric);
    action.replacePlan(p.recommend(), p.metric(), p.summary(), p.reason(), p.plan(), p.source(), ctx.co2(),
        p.expectedCo2After(), ctx.forecastCo2(), p.acTargetTemp());
    return action;
  }

  /**
   * LLM(실패하면 규칙)이 고른 조치에 서버 숫자를 채운다. 창문 분·예상 효과는 현재와 30분 뒤 예측 중 큰 값에서 시작.
   * 지금 CO2 가 기준 이상이면 창문 열기 단계가 꼭 있어야 한다: LLM 결과에 없으면 규칙 기반(창문 대안만)으로 대체
   */
  private Planned plan(PlanContext ctx, Metric metric) {
    Optional<RawPlan> byLlm = llmPlanner.plan(ctx)
        .filter(p -> !ctx.co2Over() || PlanCalculator.windowOpen(p.steps()).isPresent());
    RawPlan raw = byLlm.orElseGet(() -> ctx.co2Over() ? rulePlanner.planWithWindow(ctx) : rulePlanner.plan(ctx));
    List<PlanStep> steps = PlanCalculator.complete(raw.steps(), ctx.startCo2());
    return new Planned(
        PlanCalculator.windowOpen(steps).isPresent() ? Recommend.OPEN : Recommend.CLOSE,
        metric,
        PlanCalculator.summary(raw.summary(), steps),
        PlanCalculator.stripNumbers(raw.reason()),
        planCodec.write(steps),
        byLlm.isPresent() ? PlanSource.LLM : PlanSource.RULE,
        PlanCalculator.expectedCo2After(steps, ctx.startCo2()),
        steps.stream().filter(s -> s.type() == StepType.AC_ON).map(PlanStep::targetTemp).findFirst().orElse(null));
  }

  /** 서버 숫자를 채운 추천 (저장 전) */
  private record Planned(Recommend recommend, Metric metric, String summary, String reason, String plan,
      PlanSource source, Integer expectedCo2After, Integer acTargetTemp) {}

  /** 민원 추천 재료. 실내 값은 최근 10분 측정, 없으면 접수 스냅샷 */
  PlanContext context(Complaint c, PlanContext.Previous previous, LocalDateTime now) {
    ReadingSnapshot reading = latest(c.getRoom().getId(), now);
    Integer co2 = reading != null && reading.getCo2() != null ? reading.getCo2() : c.getCo2Snapshot();
    Integer temp = reading != null && reading.getTemp() != null ? reading.getTemp()
        : c.getTempSnapshot() == null ? null : (int) Math.round(c.getTempSnapshot().doubleValue());
    Integer humidity = reading != null && reading.getHumidity() != null ? reading.getHumidity() : c.getHumiditySnapshot();
    return build(c.getRoom().getId(), c.getCategoryList(), c.getContent(), co2, temp, humidity, c.getCo2Snapshot(),
        c.getTempSnapshot() == null ? null : c.getTempSnapshot().doubleValue(), c.getHumiditySnapshot(), previous,
        null, now);
  }

  /** 민원 없는 조치(예측·직접 환기)의 추천 재료. 분류는 답답해요, 상황 문구는 지금 CO2·조치 예상값으로 */
  PlanContext roomContext(Action action, PlanContext.Previous previous, LocalDateTime now) {
    Long roomId = action.getRoom().getId();
    ReadingSnapshot reading = latest(roomId, now);
    Integer co2 = reading == null ? null : reading.getCo2();
    return build(roomId, List.of(ComplaintCategory.STUFFY), null, co2,
        reading == null ? null : reading.getTemp(), reading == null ? null : reading.getHumidity(),
        null, null, null, previous, alert(action, co2, thresholdService.current().co2()), now);
  }

  /**
   * "CO2 기준 초과 (현재 1,150ppm, 기준 1,000ppm)" / "14:30 에 CO2 1,100ppm 예상 (현재 920ppm, 기준 1,000ppm)" /
   * "환기 필요 (현재 920ppm, 기준 1,000ppm)"
   */
  static String alert(Action action, Integer co2, int threshold) {
    String values = "현재 " + ppm(co2) + ", 기준 " + ppm(threshold);
    if (co2 != null && co2 >= threshold) {
      return "CO2 기준 초과 (" + values + ")";
    }
    if (action.getPredictedValue() != null && action.getExpectedAt() != null) {
      return action.getExpectedAt().format(HH_MM) + " 에 CO2 " + ppm(action.getPredictedValue()) + " 예상 (" + values + ")";
    }
    return "환기 필요 (" + values + ")";
  }

  private static String ppm(Integer value) {
    return value == null ? "모름" : String.format(Locale.KOREA, "%,dppm", value);
  }

  private ReadingSnapshot latest(Long roomId, LocalDateTime now) {
    return readingRepository.findLatestSince(List.of(roomId), now.minus(AirQualityJudge.FRESH_WINDOW))
        .stream().findFirst().orElse(null);
  }

  private PlanContext build(Long roomId, List<ComplaintCategory> categories, String content, Integer co2, Integer temp,
      Integer humidity, Integer reportedCo2, Double reportedTemp, Integer reportedHumidity,
      PlanContext.Previous previous, String alert, LocalDateTime now) {
    Thresholds t = thresholdService.current();
    OutdoorObservation outdoor = outdoorRepository.findTopByOrderByObservedAtDesc()
        .filter(o -> o.getObservedAt().isAfter(now.minus(OUTDOOR_FRESH))).orElse(null);
    CurrentAndNext cn = ClassScheduleService.currentAndNext(
        classScheduleService.classesOn(roomId, now.toLocalDate()), now.toLocalTime());
    ClassSchedule targetClass = cn.current() != null ? cn.current() : cn.next();
    Integer co2In30 = predictionLookup.co2At(roomId, now.plus(FORECAST_AHEAD)).orElse(null);
    Integer co2AtClassEnd = targetClass == null ? null
        : predictionLookup.co2At(roomId, now.toLocalDate().atTime(targetClass.getEndTime())).orElse(null);
    return new PlanContext(categories, content, co2, temp, humidity, t,
        outdoor == null || outdoor.getTemp() == null ? null : outdoor.getTemp().doubleValue(),
        outdoor == null ? null : outdoor.getHumidity(),
        outdoor == null ? null : outdoor.getPm10(),
        rainForecast.firstRainWithin(now, RAIN_WITHIN).isPresent(),
        actionRepository.existsByRoomIdAndStatusAndWindowOpenedAtIsNotNullAndWindowClosedAtIsNull(roomId,
            ActionStatus.IN_PROGRESS),
        classLabel(cn.current(), true), classLabel(cn.next(), false),
        reportedCo2, reportedTemp, reportedHumidity, co2In30, co2AtClassEnd, previous, alert);
  }

  private static String classLabel(ClassSchedule c, boolean current) {
    if (c == null) {
      return null;
    }
    return c.getCourseName() + " (" + (current ? c.getEndTime().format(HH_MM) + "까지"
        : c.getStartTime().format(HH_MM) + " 시작") + ")";
  }

  /** 조치 지표: 답답함·냄새 CO2, 더움·추움 온도, 습함 습도, 고장 없음 */
  private static Metric metric(PlanContext ctx) {
    return switch (ctx.primaryCategory()) {
      case STUFFY, SMELL -> Metric.CO2;
      case HOT, COLD -> Metric.TEMP;
      case HUMID -> Metric.HUMIDITY;
      case BROKEN -> null;
    };
  }
}
