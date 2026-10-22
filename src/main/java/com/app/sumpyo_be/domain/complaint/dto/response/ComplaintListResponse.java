package com.app.sumpyo_be.domain.complaint.dto.response;

import com.app.sumpyo_be.domain.complaint.entity.ComplaintStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** GET /api/complaints : 민원 접수함 (최신순 무한 스크롤) */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "민원 접수함 목록")
public class ComplaintListResponse {

  @Schema(description = "탭별 개수 (status·cursor 를 뺀 같은 필터 기준)")
  private Counts counts;

  @Schema(description = "민원 목록 (접수 최신순)")
  private List<Item> content;

  @Schema(description = "다음 페이지 커서 (마지막 항목의 접수 시각·ID, Base64). 마지막 페이지면 null",
      example = "MjAyNi0xMC0yMVQxNDowMDowMHwxNQ")
  private String nextCursor;

  @Schema(description = "다음 페이지가 있는지", example = "true")
  private boolean hasNext;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "ComplaintCounts", description = "탭별 민원 수")
  public static class Counts {

    @Schema(description = "전체", example = "12")
    private long all;

    @Schema(description = "접수", example = "7")
    private long received;

    @Schema(description = "처리 중", example = "3")
    private long inProgress;

    @Schema(description = "완료", example = "2")
    private long done;
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "ComplaintListItem", description = "민원 카드")
  public static class Item {

    @Schema(description = "민원 ID", example = "15")
    private Long complaintId;

    @Schema(description = "강의실 (건물 + 호수)", example = "북악관 618호")
    private String room;

    @Schema(description = "민원 분류 이름 (보낸 순서)", example = "[\"답답해요\"]")
    private List<String> categoryLabels;

    @Schema(description = "내용 미리보기 (50자, 넘으면 …). 내용이 없으면 null", example = "5교시만 되면 너무 답답하고 졸려요…")
    private String preview;

    @Schema(description = "처리 상태 (RECEIVED: 접수, IN_PROGRESS: 처리 중, DONE: 완료)", example = "RECEIVED")
    private ComplaintStatus status;

    @Schema(description = "접수 시각 (KST). '6분 전' 은 프론트에서", example = "2026-10-21T14:00:00")
    private LocalDateTime createdAt;
  }
}
