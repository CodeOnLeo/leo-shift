package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.dto.ColorUpdateRequest;
import io.github.codeonleo.leoshift.dto.NotificationSettingsRequest;
import io.github.codeonleo.leoshift.dto.NotificationSettingsResponse;
import io.github.codeonleo.leoshift.dto.PatternSettingsRequest;
import io.github.codeonleo.leoshift.dto.PatternSettingsResponse;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarPattern;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.service.CalendarAccessService;
import io.github.codeonleo.leoshift.service.CalendarPatternService;
import io.github.codeonleo.leoshift.service.NotificationPreferenceService;
import io.github.codeonleo.leoshift.service.SettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final List<String> ALLOWED_CODES = List.of("D", "A", "N", "V", "O");

    private final CalendarAccessService calendarAccessService;
    private final CalendarPatternService calendarPatternService;
    private final SettingsService settingsService;
    private final NotificationPreferenceService preferenceService;

    public SettingsController(CalendarAccessService calendarAccessService,
                              CalendarPatternService calendarPatternService,
                              SettingsService settingsService,
                              NotificationPreferenceService preferenceService) {
        this.calendarAccessService = calendarAccessService;
        this.calendarPatternService = calendarPatternService;
        this.settingsService = settingsService;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<PatternSettingsResponse> getSettings(@RequestParam(required = false) Long calendarId) {
        Calendar calendar = calendarAccessService.requireView(calendarId).calendar();
        CalendarPattern pattern = calendarPatternService.findLatest(calendar).orElse(null);
        UserSettings userSettings = settingsService.getOrCreate();
        if (pattern == null) {
            return ResponseEntity.ok(new PatternSettingsResponse(false, List.of(), null, settingsService.resolveNotificationMinutes(userSettings)));
        }
        return ResponseEntity.ok(new PatternSettingsResponse(
                true,
                calendarPatternService.extractPattern(pattern),
                pattern.getPatternStartDate(),
                settingsService.resolveNotificationMinutes(userSettings)
        ));
    }

    @PutMapping
    public ResponseEntity<PatternSettingsResponse> saveSettings(@RequestParam(required = false) Long calendarId,
                                                                @Valid @RequestBody PatternSettingsRequest request) {
        validatePattern(request.pattern());
        Integer minutes = request.defaultNotificationMinutes();
        if (minutes != null && (minutes < 5 || minutes > 240)) {
            throw new IllegalArgumentException("notification_minutes_out_of_range");
        }
        Calendar calendar = calendarAccessService.requireEdit(calendarId).calendar();
        if (minutes != null) {
            settingsService.updateNotificationMinutes(minutes);
        }
        boolean applyRetroactive = Boolean.TRUE.equals(request.applyRetroactive());
        CalendarPattern saved = calendarPatternService.savePattern(calendar, request.pattern(), request.patternStartDate(), applyRetroactive);
        UserSettings userSettings = settingsService.getOrCreate();
        return ResponseEntity.ok(new PatternSettingsResponse(
                true,
                calendarPatternService.extractPattern(saved),
                saved.getPatternStartDate(),
                settingsService.resolveNotificationMinutes(userSettings)
        ));
    }

    @GetMapping("/notifications")
    public NotificationSettingsResponse getNotificationSettings() {
        return new NotificationSettingsResponse(preferenceService.fetchMinutes());
    }

    @PutMapping("/notifications")
    public NotificationSettingsResponse updateNotifications(@Valid @RequestBody NotificationSettingsRequest request) {
        return new NotificationSettingsResponse(preferenceService.updateMinutes(request.minutes()));
    }

    @PutMapping("/color")
    public ResponseEntity<Void> updateColor(@Valid @RequestBody ColorUpdateRequest request) {
        settingsService.updateColorTag(request.color());
        return ResponseEntity.noContent().build();
    }

    private void validatePattern(List<String> pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern_required");
        }
        for (String code : pattern) {
            if (!StringUtils.hasText(code) || !ALLOWED_CODES.contains(code.trim().toUpperCase())) {
                throw new IllegalArgumentException("invalid_shift_code");
            }
        }
    }
}
