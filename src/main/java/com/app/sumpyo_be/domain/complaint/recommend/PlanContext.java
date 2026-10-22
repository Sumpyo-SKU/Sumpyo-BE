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
    Previous previous
) {

  /** 미세먼지(PM10) 나쁨 이상 (81 ㎍/㎥ 부터) */
  public static final int PM10_BAD_FROM = 81;

  public record Previous(String summary, List<PlanStep> steps) {}

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
