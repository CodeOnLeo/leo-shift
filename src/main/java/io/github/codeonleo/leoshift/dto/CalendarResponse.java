package io.github.codeonleo.leoshift.dto;

import java.util.List;
import java.util.Map;

public record CalendarResponse(
        boolean patternConfigured,
        int year,
        int month,
        List<CalendarDayDto> days,
        Map<String, Long> summary
) {
}
