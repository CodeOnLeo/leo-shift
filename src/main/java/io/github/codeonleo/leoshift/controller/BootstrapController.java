package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.dto.AuthResponse;
import io.github.codeonleo.leoshift.dto.BootstrapResponse;
import io.github.codeonleo.leoshift.dto.CalendarListResponse;
import io.github.codeonleo.leoshift.dto.CalendarResponse;
import io.github.codeonleo.leoshift.dto.CalendarShareResponse;
import io.github.codeonleo.leoshift.dto.NotificationSettingsResponse;
import io.github.codeonleo.leoshift.dto.PatternSettingsResponse;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarPattern;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.service.AuthService;
import io.github.codeonleo.leoshift.service.CalendarAccessService;
import io.github.codeonleo.leoshift.service.CalendarPatternService;
import io.github.codeonleo.leoshift.service.CalendarService;
import io.github.codeonleo.leoshift.service.CalendarShareService;
import io.github.codeonleo.leoshift.service.NotificationPreferenceService;
import io.github.codeonleo.leoshift.service.SettingsService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BootstrapController {

    private final CalendarAccessService calendarAccessService;
    private final CalendarService calendarService;
    private final CalendarShareService calendarShareService;
    private final CalendarPatternService calendarPatternService;
    private final SettingsService settingsService;
    private final NotificationPreferenceService preferenceService;
    private final AuthService authService;

    public BootstrapController(CalendarAccessService calendarAccessService,
                               CalendarService calendarService,
                               CalendarShareService calendarShareService,
                               CalendarPatternService calendarPatternService,
                               SettingsService settingsService,
                               NotificationPreferenceService preferenceService,
                               AuthService authService) {
        this.calendarAccessService = calendarAccessService;
        this.calendarService = calendarService;
        this.calendarShareService = calendarShareService;
        this.calendarPatternService = calendarPatternService;
        this.settingsService = settingsService;
        this.preferenceService = preferenceService;
        this.authService = authService;
    }

    @GetMapping("/bootstrap")
    public BootstrapResponse bootstrap(@RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month,
                                       @RequestParam(required = false) Long calendarId) {
        var access = calendarAccessService.requireView(calendarId);
        Calendar calendar = access.calendar();
        var currentUser = calendarAccessService.getCurrentUser();
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        int targetYear = year != null ? year : now.getYear();
        int targetMonth = month != null ? month : now.getMonthValue();

        // 필수 데이터
        CalendarResponse calendarResponse = calendarService.buildMonthlyCalendar(calendar, targetYear, targetMonth);
        UserSettings userSettings = settingsService.getOrCreate();
        CalendarPatternService.ResolvedPattern pattern = calendarPatternService.findLatest(calendar).orElse(null);
        PatternSettingsResponse settingsResponse = pattern == null
                ? new PatternSettingsResponse(false, List.of(), null, settingsService.resolveNotificationMinutes(userSettings))
                : new PatternSettingsResponse(true, pattern.codes(), pattern.startDate(), settingsService.resolveNotificationMinutes(userSettings));
        NotificationSettingsResponse notificationSettings = new NotificationSettingsResponse(preferenceService.fetchMinutes());

        // 추가 데이터
        var calendars = new CalendarListResponse(calendarAccessService.listAccessible(),
                userSettings.getDefaultCalendar() != null ? userSettings.getDefaultCalendar().getId() : null);
        List<CalendarShareResponse> shares = calendarShareService.listShares(calendar.getId());
        AuthResponse.UserInfo me = authService.getCurrentUser(currentUser.getId());

        return new BootstrapResponse(
                calendars,
                settingsResponse,
                notificationSettings,
                calendarResponse,
                shares,
                me
        );
    }
}
