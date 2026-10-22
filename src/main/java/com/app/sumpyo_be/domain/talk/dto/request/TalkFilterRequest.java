package com.app.sumpyo_be.domain.talk.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PUT /api/me/talk-filter. 보낸 내용으로 통째로 교체. floors 가 비면 건물 전체 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "연락 볼 구역 저장 (여러 개). 목록 필터일 뿐 권한이 아님")
public class TalkFilterRequest {

  @ArraySchema(arraySchema = @Schema(description = "층별 필터. 빈 배열이면 건물 전체. 층 중복 불가"))
  private List<@Valid @NotNull(message = "층을 다시 골라주세요.") Floor> floors;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TalkFilterFloorRequest", description = "층 하나")
  public static class Floor {

    @Schema(description = "층 (1~건물 최고 층)", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "층을 골라주세요.")
    private Integer floor;

    @ArraySchema(arraySchema = @Schema(description = "강의실 ID 목록. 빈 배열이면 그 층 전체. "
        + "내 건물·그 층의 LECTURE 방만 (선택지: GET /api/buildings/{buildingId}/floors/{floor} 의 rooms 중 type=LECTURE). "
        + "중복은 하나로 합침"), schema = @Schema(example = "18"))
    private List<@NotNull(message = "강의실을 다시 골라주세요.") Long> roomIds;
  }
}
