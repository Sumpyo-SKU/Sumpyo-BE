package com.app.sumpyo_be.domain.air;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.sumpyo_be.domain.air.AirQualityJudge.StatusResult;
import com.app.sumpyo_be.domain.prediction.dto.projection.PredictionPoint;
import com.app.sumpyo_be.domain.room.entity.SensorLevel;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AirQualityJudgeTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 14, 0);
  private static final int THRESHOLD = 1000;
  private static final Thresholds T = Thresholds.DEFAULT;

  private static PredictionPoint pred(int minutesLater, int co2) {
    return new PredictionPoint(1L, NOW.plusMinutes(minutesLater), co2);
  }

  /** latestCo2 = null 은 최근 10분 내 측정 없음(센서 끊김) */
  private static StatusResult status(SensorLevel level, Integer latestCo2, List<PredictionPoint> preds) {
    return AirQualityJudge.status(level, latestCo2, preds, THRESHOLD, NOW);
  }

  @Nested
  class Status {

    @Test
    void 센서_없는_방은_NO_SENSOR() {
      assertThat(status(SensorLevel.NONE, null, null).status()).isEqualTo(RoomStatus.NO_SENSOR);
    }

    @Test
    void 온습도_센서만_있는_방도_CO2가_없으니_NO_SENSOR() {
      assertThat(status(SensorLevel.TH, null, List.of(pred(5, 1200))).status()).isEqualTo(RoomStatus.NO_SENSOR);
    }

    @Test
    void 센서는_있는데_10분내_측정이_없으면_NO_SENSOR() {
      assertThat(status(SensorLevel.FULL, null, List.of(pred(10, 1500))).status())
          .isEqualTo(RoomStatus.NO_SENSOR);
    }

    @Test
    void 시뮬레이터_방은_FULL과_똑같이_판정() {
      assertThat(status(SensorLevel.SIM, 1200, List.of()).status()).isEqualTo(RoomStatus.OVER);
      assertThat(status(SensorLevel.SIM, 900, List.of(pred(10, 1100))).status()).isEqualTo(RoomStatus.PREDICTED);
      assertThat(status(SensorLevel.SIM, 900, List.of()).status()).isEqualTo(RoomStatus.NORMAL);
      assertThat(status(SensorLevel.SIM, null, List.of()).status()).isEqualTo(RoomStatus.NO_SENSOR);
    }

    @Test
    void CO2가_기준_이상이면_OVER() {
      StatusResult r = status(SensorLevel.FULL, 1000, List.of());
      assertThat(r.status()).isEqualTo(RoomStatus.OVER);
      assertThat(r.minutesToExceed()).isNull();
    }

    @Test
    void 기준_미만이고_30분내_초과_예측이_있으면_PREDICTED와_남은_분() {
      StatusResult r = status(SensorLevel.FULL, 900, List.of(pred(10, 950), pred(20, 1010), pred(25, 1100)));
      assertThat(r.status()).isEqualTo(RoomStatus.PREDICTED);
      assertThat(r.minutesToExceed()).isEqualTo(20);
    }

    @Test
    void 남은_시간은_분_단위로_반올림() {
      List<PredictionPoint> half = List.of(new PredictionPoint(1L, NOW.plusSeconds(19 * 60 + 30), 1200));
      assertThat(status(SensorLevel.FULL, 900, half).minutesToExceed()).isEqualTo(20);
      List<PredictionPoint> under = List.of(new PredictionPoint(1L, NOW.plusSeconds(19 * 60 + 20), 1200));
      assertThat(status(SensorLevel.FULL, 900, under).minutesToExceed()).isEqualTo(19);
    }

    @Test
    void 정확히_30분_뒤_예측은_포함_31분_뒤는_NORMAL() {
      assertThat(status(SensorLevel.FULL, 900, List.of(pred(30, 1500))).status()).isEqualTo(RoomStatus.PREDICTED);
      assertThat(status(SensorLevel.FULL, 900, List.of(pred(31, 1500))).status()).isEqualTo(RoomStatus.NORMAL);
    }

    @Test
    void 지난_시각_예측은_무시() {
      assertThat(status(SensorLevel.FULL, 900, List.of(pred(-5, 1500))).status()).isEqualTo(RoomStatus.NORMAL);
    }

    @Test
    void 예측도_기준_미만이면_NORMAL() {
      assertThat(status(SensorLevel.FULL, 900, List.of(pred(10, 999))).status()).isEqualTo(RoomStatus.NORMAL);
    }

    @Test
    void 대기_조치_문구() {
      assertThat(AirQualityJudge.pendingActionMessage(RoomStatus.OVER, null, "요약")).isEqualTo("기준 초과 · 환기 전");
      assertThat(AirQualityJudge.pendingActionMessage(RoomStatus.PREDICTED, 20, "요약"))
          .isEqualTo("약 20분 뒤 기준 초과 예상");
      assertThat(AirQualityJudge.pendingActionMessage(RoomStatus.NORMAL, null, "민원: 답답해요")).isEqualTo("민원: 답답해요");
    }

    @Test
    void 다음_수업_팁은_환기가_필요할_때만() {
      assertThat(AirQualityJudge.nextClassTip(RoomStatus.OVER)).isEqualTo("쉬는 시간에 창문 환기 추천");
      assertThat(AirQualityJudge.nextClassTip(RoomStatus.PREDICTED)).isEqualTo("쉬는 시간에 창문 환기 추천");
      assertThat(AirQualityJudge.nextClassTip(RoomStatus.NORMAL)).isNull();
      assertThat(AirQualityJudge.nextClassTip(RoomStatus.NO_SENSOR)).isNull();
    }

    @Test
    void 경보_개수에는_OVER와_PREDICTED만() {
      assertThat(AirQualityJudge.isAlert(RoomStatus.OVER)).isTrue();
      assertThat(AirQualityJudge.isAlert(RoomStatus.PREDICTED)).isTrue();
      assertThat(AirQualityJudge.isAlert(RoomStatus.NORMAL)).isFalse();
      assertThat(AirQualityJudge.isAlert(RoomStatus.NO_SENSOR)).isFalse();
    }
  }

  @Nested
  class VentilationStatus {

    @Test
    void OVER_면_현재_CO2_빼기_기준() {
      assertThat(AirQualityJudge.overThreshold(RoomStatus.OVER, 1150, THRESHOLD)).isEqualTo(150);
      assertThat(AirQualityJudge.overThreshold(RoomStatus.OVER, 1000, THRESHOLD)).isZero();
    }

    @Test
    void OVER_가_아니면_overThreshold_는_null() {
      assertThat(AirQualityJudge.overThreshold(RoomStatus.PREDICTED, 920, THRESHOLD)).isNull();
      assertThat(AirQualityJudge.overThreshold(RoomStatus.NORMAL, 650, THRESHOLD)).isNull();
      assertThat(AirQualityJudge.overThreshold(RoomStatus.NO_SENSOR, null, THRESHOLD)).isNull();
    }

    @Test
    void 미리_알림_10분_초과까지_20분이면_10분_안에_환기() {
      assertThat(AirQualityJudge.ventilateInMinutes(RoomStatus.PREDICTED, 20, 10)).isEqualTo(10);
    }

    @Test
    void 미리_알림_시간보다_적게_남았으면_0() {
      assertThat(AirQualityJudge.ventilateInMinutes(RoomStatus.PREDICTED, 8, 15)).isZero();
    }

    @Test
    void PREDICTED_가_아니면_ventilateInMinutes_는_null() {
      assertThat(AirQualityJudge.ventilateInMinutes(RoomStatus.OVER, null, 10)).isNull();
      assertThat(AirQualityJudge.ventilateInMinutes(RoomStatus.NORMAL, null, 10)).isNull();
    }
  }

  @Nested
  class Co2LevelAndLabels {

    @Test
    void CO2_수준은_기준_대비_90퍼센트와_120퍼센트로_나뉜다() {
      assertThat(AirQualityJudge.co2Level(899, THRESHOLD)).isEqualTo(Co2Level.NORMAL);
      assertThat(AirQualityJudge.co2Level(900, THRESHOLD)).isEqualTo(Co2Level.CAUTION);
      assertThat(AirQualityJudge.co2Level(1199, THRESHOLD)).isEqualTo(Co2Level.CAUTION);
      assertThat(AirQualityJudge.co2Level(1200, THRESHOLD)).isEqualTo(Co2Level.CRITICAL);
      assertThat(AirQualityJudge.co2Level(null, THRESHOLD)).isNull();
    }

    @Test
    void cautionFrom_criticalFrom은_올림이고_co2Level과_같은_경계() {
      assertThat(AirQualityJudge.cautionFrom(1000)).isEqualTo(900);
      assertThat(AirQualityJudge.criticalFrom(1000)).isEqualTo(1200);
      assertThat(AirQualityJudge.cautionFrom(1005)).isEqualTo(905);    // 904.5 → 905
      assertThat(AirQualityJudge.criticalFrom(1005)).isEqualTo(1206);  // 1206.0
      assertThat(AirQualityJudge.cautionFrom(1003)).isEqualTo(903);    // 902.7 → 903
      for (int t = 600; t <= 1500; t++) {
        assertThat(AirQualityJudge.co2Level(AirQualityJudge.cautionFrom(t) - 1, t)).isEqualTo(Co2Level.NORMAL);
        assertThat(AirQualityJudge.co2Level(AirQualityJudge.cautionFrom(t), t)).isEqualTo(Co2Level.CAUTION);
        assertThat(AirQualityJudge.co2Level(AirQualityJudge.criticalFrom(t), t)).isEqualTo(Co2Level.CRITICAL);
      }
    }

    @Test
    void 기준_초과량은_음수면_0() {
      assertThat(AirQualityJudge.co2OverBy(1250, THRESHOLD)).isEqualTo(250);
      assertThat(AirQualityJudge.co2OverBy(700, THRESHOLD)).isZero();
      assertThat(AirQualityJudge.co2OverBy(null, THRESHOLD)).isNull();
    }

    @Test
    void 온도_라벨() {
      assertThat(AirQualityJudge.tempLabel(23, 20, 26)).isEqualTo("적정");
      assertThat(AirQualityJudge.tempLabel(28, 20, 26)).isEqualTo("살짝 더워요");
      assertThat(AirQualityJudge.tempLabel(29, 20, 26)).isEqualTo("더워요");
      assertThat(AirQualityJudge.tempLabel(18, 20, 26)).isEqualTo("살짝 추워요");
      assertThat(AirQualityJudge.tempLabel(17, 20, 26)).isEqualTo("추워요");
      assertThat(AirQualityJudge.tempLabel(null, 20, 26)).isNull();
    }

    @Test
    void 습도_라벨() {
      assertThat(AirQualityJudge.humidityLabel(61, 40, 60)).isEqualTo("조금 습해요");
      assertThat(AirQualityJudge.humidityLabel(70, 40, 60)).isEqualTo("습해요");
      assertThat(AirQualityJudge.humidityLabel(39, 40, 60)).isEqualTo("조금 건조해요");
      assertThat(AirQualityJudge.humidityLabel(30, 40, 60)).isEqualTo("건조해요");
    }
  }

  @Nested
  class ComfortScore {

    @Test
    void 모두_좋으면_100점() {
      assertThat(AirQualityJudge.comfortScore(600, 23, 50, T)).isEqualTo(100);
      assertThat(AirQualityJudge.comfortScore(400, 23, 50, T)).isEqualTo(100);
    }

    @Test
    void CO2는_600에서_1600까지_직선으로_깎인다() {
      // CO2 1100 → 50점. 50×0.6 + 100×0.25 + 100×0.15 = 70
      assertThat(AirQualityJudge.comfortScore(1100, 23, 50, T)).isEqualTo(70);
      // CO2 1600 이상 → 0점. 0 + 25 + 15 = 40
      assertThat(AirQualityJudge.comfortScore(2000, 23, 50, T)).isEqualTo(40);
    }

    @Test
    void 온습도는_범위를_벗어난_만큼_깎인다() {
      // 온도 28 (상한 26 + 2) → 80점, 습도 70 (상한 60 + 10) → 70점
      // 100×0.6 + 80×0.25 + 70×0.15 = 60 + 20 + 10.5 = 90.5 → 91
      assertThat(AirQualityJudge.comfortScore(600, 28, 70, T)).isEqualTo(91);
      // 온도 10 (하한 20 - 10) → 0점. 60 + 0 + 15 = 75
      assertThat(AirQualityJudge.comfortScore(600, 10, 50, T)).isEqualTo(75);
    }

    @Test
    void 값이_없는_항목은_빼고_가중치를_다시_나눈다() {
      // CO2 1100(50점)만 → 50
      assertThat(AirQualityJudge.comfortScore(1100, null, null, T)).isEqualTo(50);
      // CO2 없음, 온도 28(80점)·습도 50(100점) → (80×0.25 + 100×0.15) / 0.4 = 87.5 → 88
      assertThat(AirQualityJudge.comfortScore(null, 28, 50, T)).isEqualTo(88);
      assertThat(AirQualityJudge.comfortScore(null, null, null, T)).isNull();
    }
  }

  @Nested
  class ComfortLevelBoundaries {

    @Test
    void 칠십_이상_좋음_사십일에서_육십구_보통_사십_이하_나쁨_점수가_없으면_null() {
      assertThat(AirQualityJudge.comfortLevel(100)).isEqualTo(ComfortLevel.GOOD);
      assertThat(AirQualityJudge.comfortLevel(70)).isEqualTo(ComfortLevel.GOOD);
      assertThat(AirQualityJudge.comfortLevel(69)).isEqualTo(ComfortLevel.NORMAL);
      assertThat(AirQualityJudge.comfortLevel(41)).isEqualTo(ComfortLevel.NORMAL);
      assertThat(AirQualityJudge.comfortLevel(40)).isEqualTo(ComfortLevel.BAD);
      assertThat(AirQualityJudge.comfortLevel(0)).isEqualTo(ComfortLevel.BAD);
      assertThat(AirQualityJudge.comfortLevel(null)).isNull();
    }

    @Test
    void 라벨() {
      assertThat(ComfortLevel.GOOD.getLabel()).isEqualTo("좋음");
      assertThat(ComfortLevel.NORMAL.getLabel()).isEqualTo("보통");
      assertThat(ComfortLevel.BAD.getLabel()).isEqualTo("나쁨");
    }
  }
}
