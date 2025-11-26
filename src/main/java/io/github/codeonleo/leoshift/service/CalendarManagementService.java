package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.CalendarCreateRequest;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CalendarManagementService {

    private final CalendarRepository calendarRepository;
    private final CalendarAccessService calendarAccessService;
    private final SettingsService settingsService;

    @Transactional
    public Calendar createCalendar(CalendarCreateRequest request) {
        if (request == null || request.name() == null || request.name().trim().isEmpty()) {
            throw new IllegalArgumentException("calendar_name_required");
        }

        User currentUser = calendarAccessService.getCurrentUser();

        Calendar calendar = Calendar.builder()
                .owner(currentUser)
                .name(request.name().trim())
                .patternEnabled(request.patternEnabled() != null ? request.patternEnabled() : true)
                .build();

        calendar = calendarRepository.save(calendar);

        // 첫 번째 캘린더인 경우 기본 캘린더로 설정
        UserSettings settings = settingsService.getOrCreate();
        if (settings.getDefaultCalendar() == null) {
            settingsService.setDefaultCalendar(calendar);
        }

        return calendar;
    }
}
