package io.github.codeonleo.leoshift.dto;

import java.util.List;

public record CalendarListResponse(
        List<CalendarSummaryResponse> calendars,
        Long defaultCalendarId,
        List<CalendarSummaryResponse> ownedCalendars,
        List<CalendarSummaryResponse> sharedCalendars
) {
    public CalendarListResponse(List<CalendarSummaryResponse> calendars, Long defaultCalendarId) {
        this(
                calendars,
                defaultCalendarId,
                filterByOwnership(calendars, true),
                filterByOwnership(calendars, false)
        );
    }

    private static List<CalendarSummaryResponse> filterByOwnership(List<CalendarSummaryResponse> calendars, boolean owned) {
        if (calendars == null || calendars.isEmpty()) {
            return List.of();
        }
        return calendars.stream()
                .filter(calendar -> calendar.owned() == owned)
                .toList();
    }
}
