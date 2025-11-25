package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.repository.UserSettingsRepository;
import io.github.codeonleo.leoshift.security.UserPrincipal;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final long SINGLE_USER_ID = 1L;
    private static final int DEFAULT_NOTIFICATION_MINUTES = 60;

    private final UserSettingsRepository repository;
    private final UserRepository userRepository;

    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getId();
        }
        // 인증되지 않은 경우 또는 API 키 인증의 경우 기본값 사용
        return SINGLE_USER_ID;
    }

    @Transactional
    public UserSettings getOrCreate() {
        Long userId = currentUserId();
        return repository.findById(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalStateException("user_not_found"));
                    UserSettings created = new UserSettings();
                    created.setUser(user);
                    created.setDefaultNotificationMinutes(DEFAULT_NOTIFICATION_MINUTES);
                    return repository.save(created);
                });
    }

    @Transactional
    public UserSettings upsertPattern(List<String> pattern, LocalDate startDate, Integer defaultMinutes) {
        UserSettings settings = getOrCreate();
        settings.setPatternCodes(String.join(",", normalizePattern(pattern)));
        settings.setPatternStartDate(startDate);
        if (defaultMinutes != null) {
            settings.setDefaultNotificationMinutes(defaultMinutes);
        } else if (settings.getDefaultNotificationMinutes() == null) {
            settings.setDefaultNotificationMinutes(DEFAULT_NOTIFICATION_MINUTES);
        }
        return repository.save(settings);
    }

    @Transactional
    public UserSettings updateNotificationMinutes(int minutes) {
        UserSettings settings = getOrCreate();
        settings.setDefaultNotificationMinutes(minutes);
        return repository.save(settings);
    }

    @Transactional
    public void clearPattern() {
        UserSettings settings = getOrCreate();
        settings.setPatternCodes(null);
        settings.setPatternStartDate(null);
        repository.save(settings);
    }

    @Transactional
    public void ensurePatternExists() {
        if (!isPatternConfigured()) {
            throw new IllegalStateException("pattern_not_configured");
        }
    }

    public Optional<UserSettings> findSettings() {
        return repository.findById(currentUserId());
    }

    public Optional<UserSettings> findSettings(User user) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        return repository.findById(user.getId());
    }

    @Transactional
    public void setDefaultCalendar(io.github.codeonleo.leoshift.entity.Calendar calendar) {
        if (calendar == null) {
            throw new IllegalArgumentException("calendar_required");
        }
        UserSettings settings = getOrCreate();
        settings.setDefaultCalendar(calendar);
        repository.save(settings);
    }

    @Transactional
    public void updateColorTag(String color) {
        Long userId = currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("user_not_found"));
        user.setColorTag(color != null ? color.toUpperCase() : null);
        userRepository.save(user);
    }

    public boolean isPatternConfigured() {
        return findSettings().map(this::isPatternConfigured).orElse(false);
    }

    public boolean isPatternConfigured(UserSettings settings) {
        return settings != null
                && settings.getPatternStartDate() != null
                && !extractPattern(settings).isEmpty();
    }

    public List<String> extractPattern(UserSettings settings) {
        if (settings == null || !StringUtils.hasText(settings.getPatternCodes())) {
            return Collections.emptyList();
        }
        return Arrays.stream(settings.getPatternCodes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }

    public int resolveNotificationMinutes(UserSettings settings) {
        if (settings == null || settings.getDefaultNotificationMinutes() == null) {
            return DEFAULT_NOTIFICATION_MINUTES;
        }
        return settings.getDefaultNotificationMinutes();
    }

    private List<String> normalizePattern(List<String> pattern) {
        if (pattern == null) {
            return Collections.emptyList();
        }
        return pattern.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toList());
    }
}
