package com.app.sumpyo_be.domain.room.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/rooms/{roomId}/forecast/next-class : 홈 [수업 중 예보] 카드 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "수업 중 CO2 예보 (진행 중인 수업, 없으면 오늘 남은 다음 수업)")
public class NextClassForecastResponse {

  @Schema(description = "과목명", example = "데이터크롤링")
  private String courseName;

  @Schema(description = "수업 시작 (HH:mm)", example = "12:00")
  private String start;

  @Schema(description = "수업 종료 (HH:mm)", example = "13:15")
  private String end;

  @Schema(description = "수업 종료 시각 예상 CO2 (ppm). 종료 ±10분 안에 예측이 없으면 null", example = "1280")
  private Integer co2AtEnd;

  @Schema(description = "CO2 기준값 (내 설정, 비로그인 1000)", example = "1000")
  private int threshold;

  @Schema(description = "수업 중 환기가 필요한지. 예측이 없으면 null", example = "true")
  private Boolean needsVentilation;

  @Schema(description = "카드 문구 (수업 전에 환기가 필요해요 / 수업 후반엔 환기가 필요해요 / 수업 내내 쾌적할 거예요 / "
      + "예측 데이터를 모으는 중이에요)", example = "수업 후반엔 환기가 필요해요")
  private String headline;

  @Schema(description = "예측 가정 (predict.py 는 창문이 닫힌 상태로 예측)", example = "창문을 닫아두면")
  private String condition;
}
