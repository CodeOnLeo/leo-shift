package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CalendarDayDto(
        LocalDate date,
        String baseCode,
        String effectiveCode,
        List<String> memos,  // 기존 호환성 유지 (deprecated)
        List<String> anniversaryMemos,
        List<String> yearlyMemos,
        boolean hasException,
        AuthorDto memoAuthor,  // 기존 호환성 유지 (deprecated)
        LocalDateTime updatedAt,
        List<MemoDto> dayMemos  // 새로운 다중 메모 필드
) {
}
