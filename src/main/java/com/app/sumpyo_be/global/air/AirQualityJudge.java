package com.app.sumpyo_be.global.air;

import com.app.sumpyo_be.global.prediction.dto.PredictionPoint;
import com.app.sumpyo_be.global.room.entity.SensorLevel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 공기질 판단 규칙 모음. 상태/CO2 수준/온습도 라벨/쾌적도 점수는 모두 여기서만 계산한다.
 * DB·Spring 에 의존하지 않는 순수 함수라 단위 테스트로 규칙을 고정한다.
 */
public final class AirQualityJudge {

  private AirQualityJudge() {}

  /** 이 시간 안의 측정값만 "최신 값"으로 본다. 넘으면 센서 끊김 → NO_SENSOR */
  public static final Duration FRESH_WINDOW = Duration.ofMinutes(10);
  /** 이 시간 안에 기준 초과 예측이 있으면 PREDICTED */
  public static final Duration PREDICTION_HORIZON = Duration.ofMinutes(30);

  // ---- CO2 수준 (기준 대비 비율) ----
  static final int CAUTION_PERCENT = 90;    // 기준의 90% 이상 CAUTION
  static final int CRITICAL_PERCENT = 120;  // 기준의 120% 이상 CRITICAL

  // ---- 온습도 라벨 ----
  /** 적정 범위를 이 값 이내로 벗어나면 "살짝/조금", 더 벗어나면 그냥 "더워요/습해요" 등 */
  static final int TEMP_SLIGHT_MARGIN = 2;
  static final int HUMIDITY_SLIGHT_MARGIN = 2;

  // ---- 쾌적도 점수 (0~100) ----
  // score = CO2점수×0.60 + 온도점수×0.25 + 습도점수×0.15
  //   CO2점수 : 600ppm 이하 100점, 1600ppm 이상 0점, 그 사이 직선 (100ppm 당 10점)
  //   온도점수: 적정 범위 안 100점, 벗어난 1℃ 당 10점 감점 (최저 0점)
  //   습도점수: 적정 범위 안 100점, 벗어난 1%p 당 3점 감점 (최저 0점)
  // 값이 없는 항목(센서 실패)은 빼고 남은 가중치로 다시 나눈다. 셋 다 없으면 null.
  static final double CO2_WEIGHT = 0.60;
  static final double TEMP_WEIGHT = 0.25;
  static final double HUMIDITY_WEIGHT = 0.15;
  static final int CO2_BEST = 600;
  static final int CO2_WORST = 1600;
  static final double TEMP_PENALTY_PER_DEGREE = 10;
  static final double HUMIDITY_PENALTY_PER_PERCENT = 3;

  public record StatusResult(RoomStatus status, Integer minutesToExceed) {}


  /**
   * @param latestCo2   최근 10분 내 최신 측정의 CO2. 측정이 없으면(센서 끊김) null
   * @param predictions 해당 방의 예측값 (구간 밖 값은 여기서 걸러낸다)
   */
  public static StatusResult status(SensorLevel sensorLevel, Integer latestCo2,
      List<PredictionPoint> predictions, int co2Threshold, LocalDateTime now) {
    // SIM 은 FULL 과 똑같이 판정한다. TH(온습도만)/NONE 은 CO2 가 없으니 NO_SENSOR
    if (!sensorLevel.hasCo2() || latestCo2 == null) {
      return new StatusResult(RoomStatus.NO_SENSOR, null);
    }
    if (latestCo2 >= co2Threshold) {
      return new StatusResult(RoomStatus.OVER, null);
    }
    return firstExceedAt(predictions, co2Threshold, now)
        .map(at -> new StatusResult(RoomStatus.PREDICTED, ceilMinutes(Duration.between(now, at))))
        .orElseGet(() -> new StatusResult(RoomStatus.NORMAL, null));
  }

  /** (now, now+30분] 안에서 예측 CO2 가 처음으로 기준 이상이 되는 시각 */
  static Optional<LocalDateTime> firstExceedAt(List<PredictionPoint> predictions, int co2Threshold,
      LocalDateTime now) {
    if (predictions == null) {
      return Optional.empty();
    }
    LocalDateTime horizon = now.plus(PREDICTION_HORIZON);
    return predictions.stream()
        .filter(p -> p.co2() != null && p.co2() >= co2Threshold)
        .map(PredictionPoint::targetAt)
        .filter(t -> t.isAfter(now) && !t.isAfter(horizon))
        .min(Comparator.naturalOrder());
  }

  private static int ceilMinutes(Duration d) {
    return (int) ((d.getSeconds() + 59) / 60);
  }

  public static boolean isAlert(RoomStatus status) {
    return status == RoomStatus.OVER || status == RoomStatus.PREDICTED;
  }

  /** 대기 중 조치 문구: OVER "기준 초과 · 환기 전", PREDICTED "약 N분 뒤 기준 초과 예상", 그 외 조치 요약 */
  public static String pendingActionMessage(RoomStatus status, Integer minutesToExceed, String actionSummary) {
    return switch (status) {
      case OVER -> "기준 초과 · 환기 전";
      case PREDICTED -> "약 " + minutesToExceed + "분 뒤 기준 초과 예상";
      case NORMAL, NO_SENSOR -> actionSummary;
    };
  }

  /** CAUTION 이 시작되는 CO2 (기준 × 0.9, 올림). 설정 화면 표시와 판정이 같은 값을 쓴다 */
  public static int cautionFrom(int co2Threshold) {
    return Math.ceilDiv(co2Threshold * CAUTION_PERCENT, 100);
  }

  /** CRITICAL 이 시작되는 CO2 (기준 × 1.2, 올림) */
  public static int criticalFrom(int co2Threshold) {
    return Math.ceilDiv(co2Threshold * CRITICAL_PERCENT, 100);
  }

  public static Co2Level co2Level(Integer co2, int co2Threshold) {
    if (co2 == null) {
      return null;
    }
    if (co2 >= criticalFrom(co2Threshold)) {
      return Co2Level.CRITICAL;
    }
    if (co2 >= cautionFrom(co2Threshold)) {
      return Co2Level.CAUTION;
    }
    return Co2Level.NORMAL;
  }

  /** 기준보다 얼마나 높은지. 기준 미만이면 0 */
  public static Integer co2OverBy(Integer co2, int co2Threshold) {
    return co2 == null ? null : Math.max(0, co2 - co2Threshold);
  }

  public static String tempLabel(Integer temp, int min, int max) {
    return rangeLabel(temp, min, max, TEMP_SLIGHT_MARGIN,
        "살짝 더워요", "더워요", "살짝 추워요", "추워요");
  }

  public static String humidityLabel(Integer humidity, int min, int max) {
    return rangeLabel(humidity, min, max, HUMIDITY_SLIGHT_MARGIN,
        "조금 습해요", "습해요", "조금 건조해요", "건조해요");
  }

  private static String rangeLabel(Integer value, int min, int max, int slightMargin,
      String slightHigh, String high, String slightLow, String low) {
    if (value == null) {
      return null;
    }
    if (value > max) {
      return value - max <= slightMargin ? slightHigh : high;
    }
    if (value < min) {
      return min - value <= slightMargin ? slightLow : low;
    }
    return "적정";
  }

  public static Integer comfortScore(Integer co2, Integer temp, Integer humidity, Thresholds t) {
    double weighted = 0;
    double weights = 0;
    if (co2 != null) {
      double co2Score = 100.0 * (CO2_WORST - co2) / (CO2_WORST - CO2_BEST);
      weighted += clamp(co2Score) * CO2_WEIGHT;
      weights += CO2_WEIGHT;
    }
    if (temp != null) {
      weighted += rangeScore(temp, t.tempMin(), t.tempMax(), TEMP_PENALTY_PER_DEGREE) * TEMP_WEIGHT;
      weights += TEMP_WEIGHT;
    }
    if (humidity != null) {
      weighted += rangeScore(humidity, t.humidityMin(), t.humidityMax(), HUMIDITY_PENALTY_PER_PERCENT)
          * HUMIDITY_WEIGHT;
      weights += HUMIDITY_WEIGHT;
    }
    return weights == 0 ? null : (int) Math.round(weighted / weights);
  }

  private static double rangeScore(int value, int min, int max, double penaltyPerUnit) {
    int distance = value > max ? value - max : value < min ? min - value : 0;
    return clamp(100 - distance * penaltyPerUnit);
  }

  private static double clamp(double score) {
    return Math.max(0, Math.min(100, score));
  }

  /** 다음 수업 카드의 팁. 환기가 필요할 때(OVER/PREDICTED)만, 아니면 null */
  public static String nextClassTip(RoomStatus status) {
    return isAlert(status) ? "쉬는 시간에 창문 환기 추천" : null;
  }
}
