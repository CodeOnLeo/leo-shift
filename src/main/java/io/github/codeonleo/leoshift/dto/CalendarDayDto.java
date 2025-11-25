package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CalendarDayDto(
        LocalDate date,
        String baseCode,
        String effectiveCode,
        List<String> memos,
        List<String> anniversaryMemos,
        List<String> yearlyMemos,
        boolean hasException,
        AuthorDto memoAuthor,
        LocalDateTime updatedAt
) {
}
