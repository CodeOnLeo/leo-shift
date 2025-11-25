package io.github.codeonleo.leoshift.dto;

import java.util.List;

public record CalendarListResponse(
        List<CalendarSummaryResponse> calendars,
        Long defaultCalendarId
) {
}
