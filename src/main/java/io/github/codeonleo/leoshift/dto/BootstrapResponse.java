package io.github.codeonleo.leoshift.dto;

import java.util.List;

public record BootstrapResponse(
        CalendarListResponse calendars,
        PatternSettingsResponse settings,
        NotificationSettingsResponse notificationSettings,
        CalendarResponse calendar,
        List<CalendarShareResponse> shares,
        AuthResponse.UserInfo me
) {
}
