package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.SimpleDayDto;
import io.github.codeonleo.leoshift.dto.TodayResponse;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.UserSettings;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TodayService {

    private final ScheduleService scheduleService;
    private final SettingsService settingsService;
    private final CalendarAccessService calendarAccessService;

    public TodayResponse buildTodayView(Long calendarId) {
        CalendarAccessService.CalendarAccess access = calendarAccessService.requireView(calendarId);
        Calendar calendar = access.calendar();
        UserSettings ownerSettings = settingsService.findSettings(calendar.getOwner())
                .orElse(null);
        boolean configured = ownerSettings != null && settingsService.isPatternConfigured(ownerSettings);
        if (!configured) {
            return new TodayResponse(false, null, List.of());
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        SimpleDayDto todayDto = scheduleService.resolveDay(today, calendar)
                .map(this::toSimple)
                .orElse(null);
        List<SimpleDayDto> upcoming = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            LocalDate date = today.plusDays(i);
            scheduleService.resolveDay(date, calendar).ifPresent(schedule -> upcoming.add(toSimple(schedule)));
        }
        return new TodayResponse(true, todayDto, upcoming);
    }

    private SimpleDayDto toSimple(DaySchedule schedule) {
        ShiftCodeDefinition definition = ShiftCodeDefinition.fromCode(schedule.effectiveCode());
        return new SimpleDayDto(
                schedule.date(),
                schedule.effectiveCode(),
                definition.label(),
                definition.timeRangeLabel(),
                schedule.combinedMemos()
        );
    }
}
