package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DayDetailResponse(
        LocalDate date,
        String baseCode,
        String effectiveCode,
        String shiftLabel,
        String timeRange,
        String memo,  // 기존 호환성 유지 (deprecated)
        String anniversaryMemo,
        boolean repeatYearly,
        List<String> yearlyMemos,
        AuthorDto memoAuthor,  // 기존 호환성 유지 (deprecated)
        LocalDateTime updatedAt,
        List<MemoDto> dayMemos  // 새로운 다중 메모 필드
) {
}
