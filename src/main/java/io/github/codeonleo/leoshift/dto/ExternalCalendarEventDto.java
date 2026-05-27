package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;

public record ExternalCalendarEventDto(
        Long sourceId,
        String sourceName,
        String color,
        String displayMode,
        String dateTextColor,
        String borderColor,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        boolean allDay,
        String location
) {
}
