package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.dto.NotificationSettingsRequest;
import io.github.codeonleo.leoshift.dto.NotificationSettingsResponse;
import io.github.codeonleo.leoshift.dto.PatternSettingsRequest;
import io.github.codeonleo.leoshift.dto.PatternSettingsResponse;
import io.github.codeonleo.leoshift.dto.ColorUpdateRequest;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.service.NotificationPreferenceService;
import io.github.codeonleo.leoshift.service.SettingsService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final List<String> ALLOWED_CODES = List.of("D", "A", "N", "V", "O");

    private final SettingsService settingsService;
    private final NotificationPreferenceService preferenceService;

    public SettingsController(SettingsService settingsService, NotificationPreferenceService preferenceService) {
        this.settingsService = settingsService;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ResponseEntity<PatternSettingsResponse> getSettings() {
        Optional<UserSettings> settingsOpt = settingsService.findSettings();
        if (settingsOpt.isEmpty() || !settingsService.isPatternConfigured(settingsOpt.get())) {
            return ResponseEntity.ok(new PatternSettingsResponse(false, List.of(), null, null));
        }
        UserSettings settings = settingsOpt.get();
        return ResponseEntity.ok(new PatternSettingsResponse(
                true,
                settingsService.extractPattern(settings),
                settings.getPatternStartDate(),
                settingsService.resolveNotificationMinutes(settings)
        ));
    }

    @PutMapping
    public ResponseEntity<PatternSettingsResponse> saveSettings(@Valid @RequestBody PatternSettingsRequest request) {
        validatePattern(request.pattern());
        Integer minutes = request.defaultNotificationMinutes();
        if (minutes != null && (minutes < 5 || minutes > 240)) {
            throw new IllegalArgumentException("notification_minutes_out_of_range");
        }
        UserSettings saved = settingsService.upsertPattern(request.pattern(), request.patternStartDate(), minutes);
        return ResponseEntity.ok(new PatternSettingsResponse(
                true,
                settingsService.extractPattern(saved),
                saved.getPatternStartDate(),
                settingsService.resolveNotificationMinutes(saved)
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
