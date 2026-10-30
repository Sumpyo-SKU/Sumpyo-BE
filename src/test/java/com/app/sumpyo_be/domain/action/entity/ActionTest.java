package com.app.sumpyo_be.domain.action.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ActionTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 14, 0);

  private static Action startedManual(Integer co2Before) {
    Action a = Action.manualVentilation(null, NOW);
    a.start(co2Before, 1L, NOW);
    return a;
  }

  @Test
  void 직접_환기_하기는_MANUAL_OPEN_으로_시작하고_시작_시각을_기록() {
    Action a = startedManual(650);
    assertThat(a.getSource()).isEqualTo(ActionSource.MANUAL);
    assertThat(a.getRecommend()).isEqualTo(Recommend.OPEN);
    assertThat(a.getStatus()).isEqualTo(ActionStatus.IN_PROGRESS);
    assertThat(a.getStartedAt()).isEqualTo(NOW);
    assertThat(a.getWindowOpenedAt()).isEqualTo(NOW);
  }

  @Test
  void 사용자가_완료하면_auto_completed_false_와_효과() {
    Action a = startedManual(1200);
    a.complete(850, NOW.plusMinutes(20));
    assertThat(a.getStatus()).isEqualTo(ActionStatus.DONE);
    assertThat(a.getActualCo2After()).isEqualTo(850);
    assertThat(a.getCompletedAt()).isEqualTo(NOW.plusMinutes(20));
    assertThat(a.isAutoCompleted()).isFalse();
    assertThat(a.getEffective()).isTrue();
  }

  @Test
  void 자동_완료는_auto_completed_true() {
    Action a = startedManual(1200);
    a.autoComplete(1250, NOW.plusMinutes(60));
    assertThat(a.getStatus()).isEqualTo(ActionStatus.DONE);
    assertThat(a.isAutoCompleted()).isTrue();
    assertThat(a.getEffective()).isFalse();
  }

  @Test
  void CO2_를_모르면_효과는_null() {
    Action a = startedManual(null);
    a.autoComplete(700, NOW.plusMinutes(60));
    assertThat(a.getEffective()).isNull();
  }
}
