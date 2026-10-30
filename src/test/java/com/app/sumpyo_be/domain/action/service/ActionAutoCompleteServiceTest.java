package com.app.sumpyo_be.domain.action.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ActionAutoCompleteServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 14, 0);

  private static boolean isDue(int startedMinutesAgo, Integer co2Before, Integer currentCo2) {
    return ActionAutoCompleteService.isDue(NOW.minusMinutes(startedMinutesAgo), co2Before, currentCo2, NOW);
  }

  @Test
  void CO2_가_800_이하로_떨어지면_완료() {
    assertThat(isDue(10, 1200, 800)).isTrue();
    assertThat(isDue(10, 1200, 801)).isFalse();
  }

  @Test
  void 시작_후_60분이_지나면_CO2_와_상관없이_완료() {
    assertThat(isDue(60, 1200, 1100)).isTrue();
    assertThat(isDue(59, 1200, 1100)).isFalse();
    assertThat(isDue(60, 1200, null)).isTrue();
  }

  @Test
  void 시작할_때부터_800_이하였으면_60분_규칙만() {
    assertThat(isDue(5, 650, 640)).isFalse();
    assertThat(isDue(60, 650, 640)).isTrue();
  }

  @Test
  void 시작_CO2_를_모르면_지금_값만_본다() {
    assertThat(isDue(5, null, 780)).isTrue();
    assertThat(isDue(5, null, null)).isFalse();
  }
}
