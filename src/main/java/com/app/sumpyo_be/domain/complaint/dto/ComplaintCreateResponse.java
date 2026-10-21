package com.app.sumpyo_be.domain.complaint.dto;

import com.app.sumpyo_be.domain.complaint.entity.Complaint;
import com.app.sumpyo_be.domain.complaint.entity.ComplaintCategory;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /api/complaints 응답. 완료 화면 문구는 프론트가 이 값으로 만든다 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "학생 민원 접수 결과")
public class ComplaintCreateResponse {

  @Schema(description = "민원 ID", example = "15")
  private Long complaintId;

  @Schema(description = "강의실 이름 (건물 + 호수)", example = "북악관 618호")
  private String room;

  @ArraySchema(arraySchema = @Schema(description = "접수된 민원 내용 (중복 제거, 보낸 순서)", example = "[\"HUMID\"]"))
  private List<ComplaintCategory> categories;

  @ArraySchema(arraySchema = @Schema(description = "민원 내용 한글 이름 (categories 와 같은 순서)", example = "[\"습해요\"]"))
  private List<String> categoryLabels;

  /** room·building 은 이미 로딩된 상태여야 한다 */
  public static ComplaintCreateResponse from(Complaint c) {
    List<ComplaintCategory> categories = c.getCategoryList();
    return ComplaintCreateResponse.builder()
        .complaintId(c.getId())
        .room(c.getRoom().getBuilding().getName() + " " + c.getRoom().getNumber() + "호")
        .categories(categories)
        .categoryLabels(categories.stream().map(ComplaintCategory::getLabel).toList())
        .build();
  }
}
