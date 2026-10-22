package com.app.sumpyo_be.domain.complaint.recommend;

import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.action.entity.StepType;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 규칙 기반 추천 (LLM 키 없음·타임아웃·검증 실패 때). 민원 분류가 여러 개면 첫 분류 기준.
 * 분류마다 대안을 상황에 맞는 순서로 두고, 다른 방법(retry)이면 이전 추천 다음 대안으로 순환한다.
 * - 답답해요·냄새나요: 창문 2곳 (+ 실내가 적정보다 더우면 에어컨) → 창문 4곳
 * - 더워요: 바깥이 실내보다 3°C 이상 시원하고 창문을 열 수 있으면 창문, 아니면 에어컨(적정 온도 중간값)
 * - 추워요: 열린 창문이 있으면 창문 닫기, 아니면 쉬는 시간에만 짧게 환기 안내
 * - 습해요: 바깥 습도가 10%p 이상 낮고 창문을 열 수 있으면 창문, 아니면 에어컨(제습)
 * - 설비 고장: 단계 없이 시설팀 신고 안내
 */
@Component
public class RulePlanner {

  static final int COOLER_OUTSIDE = 3;
  static final int DRIER_OUTSIDE = 10;

  public RawPlan plan(PlanContext ctx) {
    List<RawPlan> options = options(ctx);
    if (!ctx.isRetry()) {
      return options.getFirst();
    }
    // 이전 추천과 단계가 같은 대안의 다음 것. 같은 게 없으면 첫 대안 (그래도 이전과 같으면 그 다음)
    List<PlanStep> previous = ctx.previous().steps();
    for (int i = 0; i < options.size(); i++) {
      if (sameSteps(options.get(i).steps(), previous)) {
        return options.get((i + 1) % options.size());
      }
    }
    return options.getFirst();
  }

  List<RawPlan> options(PlanContext ctx) {
    return switch (ctx.primaryCategory()) {
      case STUFFY, SMELL -> {
        String reason = ctx.primaryCategory() == ComplaintCategory.SMELL
            ? "냄새는 창문을 열어 바깥 공기로 빼는 게 가장 빨라요"
            : "답답함은 CO2 가 높아서라 창문 환기가 가장 효과적이에요";
        yield List.of(
            new RawPlan(withAcIfHot(ctx, window(2)), null, reason),
            new RawPlan(withAcIfHot(ctx, window(4)), null, "창문을 더 많이 열면 더 빨리 환기돼요"));
      }
      case HOT -> {
        RawPlan byWindow = new RawPlan(window(2), null, "바깥이 실내보다 시원해서 창문 환기가 효과적이에요");
        RawPlan byAc = new RawPlan(ac(ctx.comfortableTemp()), null, "바깥도 덥거나 창문을 열기 어려워 에어컨이 나아요");
        boolean cooler = ctx.outdoorTemp() != null && ctx.temp() != null
            && ctx.outdoorTemp() <= ctx.temp() - COOLER_OUTSIDE;
        yield cooler && !ctx.windowUnsuitable() ? List.of(byWindow, byAc) : List.of(byAc, byWindow);
      }
      case COLD -> {
        RawPlan close = new RawPlan(List.of(new PlanStep(StepType.WINDOW_CLOSE, null, null, null, null)), null,
            "열린 창문으로 찬 바람이 들어와요");
        RawPlan brief = new RawPlan(List.of(), "환기는 쉬는 시간에만 짧게 해주세요", "창문을 오래 열어두면 더 추워져요");
        yield ctx.windowOpen() ? List.of(close, brief) : List.of(brief, close);
      }
      case HUMID -> {
        RawPlan byWindow = new RawPlan(window(2), null, "바깥이 더 건조해서 환기로 습도를 낮출 수 있어요");
        RawPlan byAc = new RawPlan(ac(ctx.comfortableTemp()), "에어컨을 켜서 제습해주세요", "바깥도 습해서 에어컨 제습이 나아요");
        boolean drier = ctx.outdoorHumidity() != null && ctx.humidity() != null
            && ctx.outdoorHumidity() <= ctx.humidity() - DRIER_OUTSIDE;
        yield drier && !ctx.windowUnsuitable() ? List.of(byWindow, byAc) : List.of(byAc, byWindow);
      }
      case BROKEN -> List.of(new RawPlan(List.of(), "시설팀에 고장 신고해주세요", "설비 고장은 시설팀 확인이 필요해요"));
    };
  }

  private static List<PlanStep> window(int windows) {
    return List.of(new PlanStep(StepType.WINDOW_OPEN, null, windows, null, null));
  }

  private static List<PlanStep> ac(int targetTemp) {
    return List.of(new PlanStep(StepType.AC_ON, null, null, targetTemp, null));
  }

  /** 에어컨은 공기를 바꾸지 못하므로 실내가 적정보다 더울 때만 더한다 */
  private static List<PlanStep> withAcIfHot(PlanContext ctx, List<PlanStep> steps) {
    if (!ctx.hotterThanRange()) {
      return steps;
    }
    List<PlanStep> withAc = new ArrayList<>(steps);
    withAc.addAll(ac(ctx.comfortableTemp()));
    return withAc;
  }

  /** 종류·창문 수가 같으면 같은 추천 (라벨·분·희망 온도는 보지 않는다) */
  static boolean sameSteps(List<PlanStep> a, List<PlanStep> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (a.get(i).type() != b.get(i).type() || !Objects.equals(a.get(i).windows(), b.get(i).windows())) {
        return false;
      }
    }
    return true;
  }
}
