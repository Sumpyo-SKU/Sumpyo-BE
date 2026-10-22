package com.app.sumpyo_be.domain.complaint.recommend;

import com.app.sumpyo_be.domain.action.entity.PlanStep;
import com.app.sumpyo_be.domain.air.Thresholds;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import java.util.List;

/**
 * 민원 추천 재료.
 * @param co2 / temp / humidity  실내 현재 값 (최근 10분 측정, 없으면 접수 스냅샷). 모르면 null
 * @param outdoorTemp / outdoorHumidity / pm10  바깥 공기 (최근 관측). 모르면 null
 * @param rainSoon        1시간 안 비 예보
 * @param windowOpen      이 방에 창문이 열린 조치가 있는지
 * @param currentClass    "데이터크롤링 (14:45까지)", 없으면 null
 * @param nextClass       "알고리즘 (15:00 시작)", 없으면 null
 * @param reportedCo2 / reportedTemp / reportedHumidity  민원 접수 시점 측정값 (스냅샷). 없으면 null
 * @param co2In30         그대로 두면 30분 뒤 CO2 (predict.py 예측), 없으면 null
 * @param co2AtClassEnd   그대로 두면 수업(지금, 없으면 다음) 종료 시각 CO2, 없으면 null
 * @param previous        다른 방법(retry)이면 지금까지의 추천 (요약·단계), 아니면 null
 */
public record PlanContext(
    List<ComplaintCategory> categories,
    String content,
    Integer co2,
    Integer temp,
    Integer humidity,
    Thresholds thresholds,
    Double outdoorTemp,
    Integer outdoorHumidity,
    Integer pm10,
    boolean rainSoon,
    boolean windowOpen,
    String currentClass,
    String nextClass,
    Integer reportedCo2,
    Double reportedTemp,
    Integer reportedHumidity,
    Integer co2In30,
    Integer co2AtClassEnd,
    Previous previous
) {

  /** 미세먼지(PM10) 나쁨 이상 (81 ㎍/㎥ 부터) */
  public static final int PM10_BAD_FROM = 81;

  public record Previous(String summary, List<PlanStep> steps) {}

  /** 예상 효과 계산의 시작값: 현재와 30분 뒤 예측 중 큰 값 (오르는 중이면 미리 반영). 둘 다 없으면 null */
  public Integer startCo2() {
    if (co2 == null || co2In30 == null) {
      return co2 != null ? co2 : co2In30;
    }
    return Math.max(co2, co2In30);
  }

  /** 화면 "그대로 두면 N ppm": 수업 종료 시각, 수업이 없으면 30분 뒤 */
  public Integer forecastCo2() {
    return co2AtClassEnd != null ? co2AtClassEnd : co2In30;
  }

  public boolean isRetry() {
    return previous != null;
  }

  public ComplaintCategory primaryCategory() {
    return categories.getFirst();
  }

  public boolean pm10Bad() {
    return pm10 != null && pm10 >= PM10_BAD_FROM;
  }

  /** 비가 오거나 미세먼지가 나쁘면 창문을 열기 어렵다 */
  public boolean windowUnsuitable() {
    return rainSoon || pm10Bad();
  }

  public boolean hotterThanRange() {
    return temp != null && temp > thresholds.tempMax();
  }

  /** 적정 온도 중간값 (희망 온도) */
  public int comfortableTemp() {
    return (thresholds.tempMin() + thresholds.tempMax()) / 2;
  }
}
