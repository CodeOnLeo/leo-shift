package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.entity.UserSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final SettingsService settingsService;

    @Transactional(readOnly = true)
    public int fetchMinutes() {
        UserSettings settings = settingsService.getOrCreate();
        return settingsService.resolveNotificationMinutes(settings);
    }

    public int updateMinutes(int minutes) {
        settingsService.updateNotificationMinutes(minutes);
        return minutes;
    }
}
