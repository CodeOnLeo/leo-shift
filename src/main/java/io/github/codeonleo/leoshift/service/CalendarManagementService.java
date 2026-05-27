package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.CalendarCreateRequest;
import io.github.codeonleo.leoshift.dto.CalendarUpdateRequest;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.repository.CalendarLeaveEntryRepository;
import io.github.codeonleo.leoshift.repository.CalendarPatternRepository;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.CalendarShareRepository;
import io.github.codeonleo.leoshift.repository.ShiftExceptionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CalendarManagementService {

    private final CalendarRepository calendarRepository;
    private final CalendarPatternRepository calendarPatternRepository;
    private final CalendarShareRepository calendarShareRepository;
    private final CalendarLeaveEntryRepository calendarLeaveEntryRepository;
    private final ShiftExceptionRepository shiftExceptionRepository;
    private final CalendarAccessService calendarAccessService;
    private final SettingsService settingsService;
    private final ScheduleTypeService scheduleTypeService;
    private final CalendarWeeklyRuleService calendarWeeklyRuleService;

    @Transactional
    public Calendar createCalendar(CalendarCreateRequest request) {
        if (request == null || request.name() == null || request.name().trim().isEmpty()) {
            throw new IllegalArgumentException("calendar_name_required");
        }

        User currentUser = calendarAccessService.getCurrentUser();

        String templateType = request.templateType() != null ? request.templateType().trim().toLowerCase() : "";
        boolean patternEnabled = request.patternEnabled() != null ? request.patternEnabled() : true;
        if ("general".equals(templateType)) {
            patternEnabled = false;
        }
        if ("empty".equals(templateType)) {
            patternEnabled = false;
        }

        Calendar calendar = Calendar.builder()
                .owner(currentUser)
                .name(request.name().trim())
                .patternEnabled(patternEnabled)
                .build();

        calendar = calendarRepository.save(calendar);
        scheduleTypeService.ensureDefaults(calendar, templateType);
        if ("general".equals(templateType)) {
            calendarWeeklyRuleService.ensureDefaultGeneralRules(calendar);
        }

        // 첫 번째 캘린더인 경우 기본 캘린더로 설정
        UserSettings settings = settingsService.getOrCreate();
        if (settings.getDefaultCalendar() == null) {
            settingsService.setDefaultCalendar(calendar);
        }

        return calendar;
    }

    @Transactional
    public Calendar updateCalendar(Long calendarId, CalendarUpdateRequest request) {
        Calendar calendar = calendarAccessService.requireOwner(calendarId);
        if (request == null) {
            throw new IllegalArgumentException("calendar_update_required");
        }
        boolean changed = false;

        String name = request.name();
        if (name != null) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("calendar_name_required");
            }
            calendar.setName(trimmed);
            changed = true;
        }

        if (request.patternEnabled() != null) {
            calendar.setPatternEnabled(request.patternEnabled());
            changed = true;
        }

        if (changed) {
            calendar = calendarRepository.save(calendar);
        }

        return calendar;
    }

    @Transactional
    public void deleteCalendar(Long calendarId) {
        Calendar calendar = calendarAccessService.requireOwner(calendarId);

        // delete dependent data first to satisfy FK constraints
        calendarPatternRepository.deleteByCalendar(calendar);
        calendarShareRepository.deleteByCalendar(calendar);
        shiftExceptionRepository.deleteByCalendar(calendar);
        calendarLeaveEntryRepository.deleteByCalendar(calendar);
        scheduleTypeService.deleteByCalendar(calendar);
        calendarWeeklyRuleService.deleteByCalendar(calendar);

        // clear default calendar if needed
        UserSettings settings = settingsService.getOrCreate();
        if (settings.getDefaultCalendar() != null && calendar.getId().equals(settings.getDefaultCalendar().getId())) {
            settingsService.clearDefaultCalendar();
        }

        calendarRepository.delete(calendar);
    }
}
