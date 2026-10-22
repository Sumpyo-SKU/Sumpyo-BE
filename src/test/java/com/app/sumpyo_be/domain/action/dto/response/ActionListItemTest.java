package com.app.sumpyo_be.domain.action.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.app.sumpyo_be.domain.action.entity.Action;
import com.app.sumpyo_be.domain.action.entity.ActionSource;
import com.app.sumpyo_be.domain.action.entity.Metric;
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

  private static Action action(ActionSource source, Recommend recommend, Metric metric) {
    Action a = mock(Action.class);
    when(a.getSource()).thenReturn(source);
    when(a.getRecommend()).thenReturn(recommend);
    when(a.getMetric()).thenReturn(metric);
    when(a.isRainClose()).thenReturn(source == ActionSource.PREDICTION && recommend == Recommend.CLOSE
        && metric == Metric.HUMIDITY);
    return a;
  }

  private static final Action CO2_OPEN = action(ActionSource.PREDICTION, Recommend.OPEN, Metric.CO2);

  @Test
  void title_CLOSE_는_닫아주세요_OPEN_은_미래이고_그_전에_수업이_시작하면_수업_전에_열어두세요() {
    assertThat(ActionListItem.title(Recommend.CLOSE, true, true)).isEqualTo("창문을 닫아주세요");
    assertThat(ActionListItem.title(Recommend.OPEN, true, true)).isEqualTo("창문 열어주세요");    // 지금이면 수업과 무관
    assertThat(ActionListItem.title(Recommend.OPEN, false, true)).isEqualTo("수업 전에 열어두세요");
    assertThat(ActionListItem.title(Recommend.OPEN, false, false)).isEqualTo("창문 열어주세요");
  }

  @Test
  void reason은_지났으면_기준_초과_아니면_남은_분_올림() {
    assertThat(ActionListItem.reason(CO2_OPEN, NOW.minusMinutes(1), NOW)).isEqualTo("기준 초과");
    assertThat(ActionListItem.reason(CO2_OPEN, NOW, NOW)).isEqualTo("기준 초과");
    assertThat(ActionListItem.reason(CO2_OPEN, NOW.plusMinutes(10), NOW)).isEqualTo("10분 뒤 기준 초과 예정");
    assertThat(ActionListItem.reason(CO2_OPEN, NOW.plusSeconds(9 * 60 + 30), NOW)).isEqualTo("10분 뒤 기준 초과 예정");
    assertThat(ActionListItem.reason(CO2_OPEN, NOW.plusMinutes(90), NOW)).isEqualTo("1시간 30분 뒤 기준 초과 예정");
  }

  @Test
  void 비_예보_CLOSE_는_남은_시간_비_예상() {
    Action rain = action(ActionSource.PREDICTION, Recommend.CLOSE, Metric.HUMIDITY);
    assertThat(ActionListItem.reason(rain, NOW.plusHours(1), NOW)).isEqualTo("1시간 뒤 비 예상");
    assertThat(ActionListItem.reason(rain, NOW.minusMinutes(5), NOW)).isEqualTo("곧 비 예상");
  }

  @Test
  void 민원_조치는_시각과_무관하게_학생_민원_접수() {
    Action complaint = action(ActionSource.COMPLAINT, Recommend.OPEN, Metric.CO2);
    assertThat(ActionListItem.reason(complaint, NOW.plusMinutes(20), NOW)).isEqualTo("학생 민원 접수");
    assertThat(ActionListItem.reason(complaint, NOW.minusMinutes(20), NOW)).isEqualTo("학생 민원 접수");
  }

  @Test
  void relative_는_60분_미만_N분_뒤_이상은_시간() {
    assertThat(ActionListItem.relative(30)).isEqualTo("30분 뒤");
    assertThat(ActionListItem.relative(59)).isEqualTo("59분 뒤");
    assertThat(ActionListItem.relative(60)).isEqualTo("1시간 뒤");
    assertThat(ActionListItem.relative(80)).isEqualTo("1시간 20분 뒤");
    assertThat(ActionListItem.relative(120)).isEqualTo("2시간 뒤");
  }
}
