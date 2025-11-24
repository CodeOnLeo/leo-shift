package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;
import java.util.List;

public record CalendarDayDto(
        LocalDate date,
        String baseCode,
        String effectiveCode,
        List<String> memos,
        List<String> yearlyMemos,
        boolean hasException
) {
}
