package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.SimpleDayDto;
import io.github.codeonleo.leoshift.dto.TodayResponse;
import io.github.codeonleo.leoshift.entity.Calendar;
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
    private final CalendarPatternService calendarPatternService;
    private final CalendarAccessService calendarAccessService;
    private final ScheduleTypeService scheduleTypeService;

    public TodayResponse buildTodayView(Long calendarId) {
        CalendarAccessService.CalendarAccess access = calendarAccessService.requireView(calendarId);
        Calendar calendar = access.calendar();
        var scheduleTypes = scheduleTypeService.listForCalendar(calendar);
        boolean configured = calendarPatternService.hasPattern(calendar);
        if (!configured) {
            return new TodayResponse(false, null, List.of(), scheduleTypes);
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        SimpleDayDto todayDto = scheduleService.resolveDay(today, calendar)
                .map(schedule -> toSimple(schedule, calendar))
                .orElse(null);
        List<SimpleDayDto> upcoming = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            LocalDate date = today.plusDays(i);
            scheduleService.resolveDay(date, calendar).ifPresent(schedule -> upcoming.add(toSimple(schedule, calendar)));
        }
        return new TodayResponse(true, todayDto, upcoming, scheduleTypes);
    }

    private SimpleDayDto toSimple(DaySchedule schedule, Calendar calendar) {
        return new SimpleDayDto(
                schedule.date(),
                schedule.effectiveCode(),
                scheduleTypeService.resolveLabel(calendar, schedule.effectiveCode()),
                scheduleTypeService.resolveTimeRange(calendar, schedule.effectiveCode()),
                schedule.combinedMemos()
        );
    }
}
