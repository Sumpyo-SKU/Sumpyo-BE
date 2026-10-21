package com.app.sumpyo_be.domain.action.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.sumpyo_be.domain.action.entity.ActionSource;
import com.app.sumpyo_be.domain.action.entity.Recommend;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ActionListItemTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 14, 0);

  @Test
  void timing은_지났거나_10분_이내_NOW_30분_이내_SOON_그_외_LATER() {
    assertThat(ActionListItem.timing(NOW.minusMinutes(5), NOW)).isEqualTo(ActionTiming.NOW);
    assertThat(ActionListItem.timing(NOW, NOW)).isEqualTo(ActionTiming.NOW);
    assertThat(ActionListItem.timing(NOW.plusMinutes(10), NOW)).isEqualTo(ActionTiming.NOW);
    assertThat(ActionListItem.timing(NOW.plusMinutes(10).plusSeconds(1), NOW)).isEqualTo(ActionTiming.SOON);
    assertThat(ActionListItem.timing(NOW.plusMinutes(30), NOW)).isEqualTo(ActionTiming.SOON);
    assertThat(ActionListItem.timing(NOW.plusMinutes(31), NOW)).isEqualTo(ActionTiming.LATER);
  }

  @Test
  void title은_추천_조치대로() {
    assertThat(ActionListItem.title(Recommend.OPEN)).isEqualTo("창문 열어주세요");
    assertThat(ActionListItem.title(Recommend.CLOSE)).isEqualTo("창문 닫아주세요");
  }

  @Test
  void reason은_지났으면_기준_초과_아니면_남은_분_올림() {
    assertThat(ActionListItem.reason(ActionSource.PREDICTION, NOW.minusMinutes(1), NOW)).isEqualTo("기준 초과");
    assertThat(ActionListItem.reason(ActionSource.PREDICTION, NOW, NOW)).isEqualTo("기준 초과");
    assertThat(ActionListItem.reason(ActionSource.PREDICTION, NOW.plusMinutes(10), NOW))
        .isEqualTo("10분 뒤 기준 초과 예정");
    assertThat(ActionListItem.reason(ActionSource.PREDICTION, NOW.plusSeconds(9 * 60 + 30), NOW))
        .isEqualTo("10분 뒤 기준 초과 예정");
  }

  @Test
  void 민원_조치는_시각과_무관하게_학생_민원_접수() {
    assertThat(ActionListItem.reason(ActionSource.COMPLAINT, NOW.plusMinutes(20), NOW)).isEqualTo("학생 민원 접수");
    assertThat(ActionListItem.reason(ActionSource.COMPLAINT, NOW.minusMinutes(20), NOW)).isEqualTo("학생 민원 접수");
  }
}
