package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;

public record ExternalCalendarEventDto(
        Long sourceId,
        String sourceName,
        String color,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        boolean allDay,
        String location
) {
}
