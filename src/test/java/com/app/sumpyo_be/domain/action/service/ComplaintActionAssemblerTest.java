package com.app.sumpyo_be.domain.action.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.sumpyo_be.domain.action.entity.Metric;
import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.action.entity.StepType;
import com.app.sumpyo_be.domain.action.service.ComplaintActionAssembler.Effect;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 예상 효과 규칙: 창문 → CO2, 에어컨만 → 온도, 둘 다 안 되면 전부 null (0 금지) */
class ComplaintActionAssemblerTest {

  private static final PlanStep WINDOW = new PlanStep(StepType.WINDOW_OPEN, "창문 2곳 열기", 2, null, 30);
  private static final PlanStep AC = new PlanStep(StepType.AC_ON, "에어컨 켜기", null, 23, null);
  private static final PlanStep CLOSE = new PlanStep(StepType.WINDOW_CLOSE, "창문 닫기", null, null, null);

  @Test
  void 에어컨만_있으면_현재_온도에서_희망_온도() {
    assertThat(ComplaintActionAssembler.effect(List.of(AC), 436, null, 23, () -> 25))
        .isEqualTo(new Effect(Metric.TEMP, 25, 23));
  }

  @Test
  void 창문이_있으면_CO2() {
    assertThat(ComplaintActionAssembler.effect(List.of(WINDOW, AC), 1250, 757, 23, () -> 25))
        .isEqualTo(new Effect(Metric.CO2, 1250, 757));
  }

  @Test
  void 창문이_있어도_CO2_를_모르면_에어컨_온도로() {
    assertThat(ComplaintActionAssembler.effect(List.of(WINDOW, AC), null, null, 23, () -> 25))
        .isEqualTo(new Effect(Metric.TEMP, 25, 23));
  }

  @Test
  void 계산할_수_없으면_전부_null() {
    assertThat(ComplaintActionAssembler.effect(List.of(AC), 436, null, 23, () -> null)).isEqualTo(Effect.NONE);
    assertThat(ComplaintActionAssembler.effect(List.of(CLOSE), 436, null, null, () -> 25)).isEqualTo(Effect.NONE);
    assertThat(ComplaintActionAssembler.effect(List.of(), 436, null, null, () -> 25)).isEqualTo(Effect.NONE);
    assertThat(ComplaintActionAssembler.effect(List.of(WINDOW), null, null, null, () -> 25)).isEqualTo(Effect.NONE);
    assertThat(Effect.NONE.metric()).isNull();
    assertThat(Effect.NONE.before()).isNull();
    assertThat(Effect.NONE.after()).isNull();
  }

  @Test
  void 현재_온도는_에어컨_효과를_낼_때만_조회한다() {
    assertThat(ComplaintActionAssembler.effect(List.of(WINDOW), 1250, 757, null, () -> {
      throw new AssertionError("창문 효과면 온도를 조회하지 않음");
    })).isEqualTo(new Effect(Metric.CO2, 1250, 757));
  }
}
