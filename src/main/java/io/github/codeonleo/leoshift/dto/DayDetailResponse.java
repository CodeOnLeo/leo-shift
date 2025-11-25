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
        String memo,
        String anniversaryMemo,
        boolean repeatYearly,
        List<String> yearlyMemos,
        AuthorDto memoAuthor,
        LocalDateTime updatedAt
) {
}
