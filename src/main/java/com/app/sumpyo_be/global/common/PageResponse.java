package com.app.sumpyo_be.global.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "무한스크롤 페이징 응답")
public class PageResponse<T> {

    @Schema(description = "이 페이지의 목록")
    private List<T> items;

    @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0")
    private int page;

    @Schema(description = "다음 페이지가 있는지 여부")
    private boolean hasNext;

    @Schema(description = "전체 개수")
    private long totalCount;

    public static int safePageOf(int page) {
        return Math.max(page, 0);
    }

    public static long offsetOf(int page, int size) {
        return (long) safePageOf(page) * size;
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalCount) {
        int safePage = safePageOf(page);
        long offset = offsetOf(safePage, size);
        return PageResponse.<T>builder()
                .items(items)
                .page(safePage)
                .hasNext(offset + items.size() < totalCount)
                .totalCount(totalCount)
                .build();
    }
}
