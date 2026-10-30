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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 민원 AI 추천 (POST /api/complaints/{id}/recommendation). LLM 이 조치를 고르고 숫자는 서버가 계산한다.
 * LLM 키 없음·타임아웃(10초)·검증 실패면 규칙 기반 (RulePlanner).
 * 지금 CO2 가 기준 이상이면 (다른 방법 포함) 창문 열기 단계가 반드시 들어간다: LLM 결과에 없으면 규칙 기반 창문 대안으로.
 * RECEIVED 민원만. 대기 중 추천이 있으면 그대로, [다른 방법](retry)이면 그 추천을 이전과 다른 새 추천으로 교체.
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
    if (waiting.isPresent() && !retry) {
      return assembler.toResponse(waiting.get());
    }

    LocalDateTime now = LocalDateTime.now(clock);
    PlanContext.Previous previous = retry ? waiting.filter(Action::hasPlan)
        .map(a -> new PlanContext.Previous(a.getSummary(), planCodec.read(a.getPlan()))).orElse(null) : null;
    PlanContext ctx = context(complaint, previous, now);

    // 지금 CO2 가 기준 이상이면 창문 열기 단계가 꼭 있어야 한다. LLM 결과에 없으면 규칙 기반(창문 대안만)으로 대체
    Optional<RawPlan> byLlm = llmPlanner.plan(ctx)
        .filter(p -> !ctx.co2Over() || PlanCalculator.windowOpen(p.steps()).isPresent());
    RawPlan raw = byLlm.orElseGet(() -> ctx.co2Over() ? rulePlanner.planWithWindow(ctx) : rulePlanner.plan(ctx));
    PlanSource source = byLlm.isPresent() ? PlanSource.LLM : PlanSource.RULE;

    // 창문 분·예상 효과는 현재와 30분 뒤 예측 중 큰 값에서 시작 (오르는 중이면 미리 반영)
    List<PlanStep> steps = PlanCalculator.complete(raw.steps(), ctx.startCo2());
    String summary = PlanCalculator.summary(raw.summary(), steps);
    String reason = PlanCalculator.stripNumbers(raw.reason());
    Integer expected = PlanCalculator.expectedCo2After(steps, ctx.startCo2());
    Recommend recommend = PlanCalculator.windowOpen(steps).isPresent() ? Recommend.OPEN : Recommend.CLOSE;
    Metric metric = metric(ctx);
    Integer acTemp = steps.stream().filter(s -> s.type() == StepType.AC_ON).map(PlanStep::targetTemp)
        .findFirst().orElse(null);
    String plan = planCodec.write(steps);

    Action action;
    if (waiting.isPresent()) {
      action = waiting.get();
      action.replacePlan(recommend, metric, summary, reason, plan, source, ctx.co2(), expected, ctx.forecastCo2(),
          acTemp);
    } else {
      action = actionRepository.save(Action.complaintPlan(complaint.getRoom(), complaintId, recommend, metric,
          summary, reason, plan, source, ctx.co2(), expected, ctx.forecastCo2(), acTemp, now));
    }
    return assembler.toResponse(action);
  }

  /** 실내 값은 최근 10분 측정, 없으면 접수 스냅샷 */
  PlanContext context(Complaint c, PlanContext.Previous previous, LocalDateTime now) {
    Long roomId = c.getRoom().getId();
    Thresholds t = thresholdService.current();
    ReadingSnapshot reading = readingRepository.findLatestSince(List.of(roomId), now.minus(AirQualityJudge.FRESH_WINDOW))
        .stream().findFirst().orElse(null);
    Integer co2 = reading != null && reading.getCo2() != null ? reading.getCo2() : c.getCo2Snapshot();
    Integer temp = reading != null && reading.getTemp() != null ? reading.getTemp()
        : c.getTempSnapshot() == null ? null : (int) Math.round(c.getTempSnapshot().doubleValue());
    Integer humidity = reading != null && reading.getHumidity() != null ? reading.getHumidity() : c.getHumiditySnapshot();

    OutdoorObservation outdoor = outdoorRepository.findTopByOrderByObservedAtDesc()
        .filter(o -> o.getObservedAt().isAfter(now.minus(OUTDOOR_FRESH))).orElse(null);
    CurrentAndNext cn = ClassScheduleService.currentAndNext(
        classScheduleService.classesOn(roomId, now.toLocalDate()), now.toLocalTime());
    ClassSchedule targetClass = cn.current() != null ? cn.current() : cn.next();
    Integer co2In30 = predictionLookup.co2At(roomId, now.plus(FORECAST_AHEAD)).orElse(null);
    Integer co2AtClassEnd = targetClass == null ? null
        : predictionLookup.co2At(roomId, now.toLocalDate().atTime(targetClass.getEndTime())).orElse(null);
    return new PlanContext(c.getCategoryList(), c.getContent(), co2, temp, humidity, t,
        outdoor == null || outdoor.getTemp() == null ? null : outdoor.getTemp().doubleValue(),
        outdoor == null ? null : outdoor.getHumidity(),
        outdoor == null ? null : outdoor.getPm10(),
        rainForecast.firstRainWithin(now, RAIN_WITHIN).isPresent(),
        actionRepository.existsByRoomIdAndStatusAndWindowOpenedAtIsNotNullAndWindowClosedAtIsNull(roomId,
            ActionStatus.IN_PROGRESS),
        classLabel(cn.current(), true), classLabel(cn.next(), false),
        c.getCo2Snapshot(), c.getTempSnapshot() == null ? null : c.getTempSnapshot().doubleValue(),
        c.getHumiditySnapshot(), co2In30, co2AtClassEnd, previous);
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
