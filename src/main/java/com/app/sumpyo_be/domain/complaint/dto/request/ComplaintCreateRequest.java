package com.app.sumpyo_be.domain.complaint.dto.request;

import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/complaints 본문. content 길이(앞뒤 공백 제거 후 500자)는 ComplaintService 에서 검사 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "학생 민원 제출")
public class ComplaintCreateRequest {

  @Schema(description = "강의실 ID (GET /api/rooms/lookup 응답의 roomId)", example = "42",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotNull(message = "위치를 선택해주세요")
  private Long roomId;

  @ArraySchema(arraySchema = @Schema(description = "민원 내용, 1개 이상 (중복은 하나로 합침). "
      + "STUFFY: 답답해요, HOT: 더워요, COLD: 추워요, HUMID: 습해요, SMELL: 냄새나요, BROKEN: 설비 고장",
      example = "[\"HUMID\"]", requiredMode = Schema.RequiredMode.REQUIRED))
  @NotEmpty(message = "불편한 점을 하나 이상 골라주세요")
  private List<@NotNull(message = "불편한 점을 다시 골라주세요") ComplaintCategory> categories;

  @Schema(description = "민원 상세 (선택, 앞뒤 공백 제거 후 최대 500자. 비어 있으면 null 로 저장)",
      example = "5교시만 되면 너무 습해요", maxLength = 500)
  private String content;

  @Schema(description = "익명 신고자 ID. 프론트가 localStorage 에 저장한 UUID (서버는 해시만 저장)",
      example = "3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "잠시 후 다시 시도해주세요")
  @Size(max = 100, message = "잠시 후 다시 시도해주세요")
  private String clientId;
}
