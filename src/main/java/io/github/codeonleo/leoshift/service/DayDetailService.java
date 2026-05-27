package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.DayDetailResponse;
import io.github.codeonleo.leoshift.dto.ExceptionUpdateRequest;
import io.github.codeonleo.leoshift.dto.MemoDto;
import io.github.codeonleo.leoshift.entity.Calendar;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DayDetailService {

    private final ScheduleService scheduleService;
    private final ExceptionService exceptionService;
    private final DayMemoService dayMemoService;
    private final CalendarLeaveService calendarLeaveService;
    private final ScheduleTypeService scheduleTypeService;

    public DayDetailResponse load(LocalDate date, Calendar calendar) {
        return scheduleService.resolveDay(date, calendar)
                .map(schedule -> toResponse(schedule, calendar))
                .orElseGet(() -> {
                    List<MemoDto> dayMemos = dayMemoService.getMemos(date, calendar);
                    return new DayDetailResponse(
                            date,
                            null,
                            null,
                            "",
                            "",
                            null,
                            null,
                            false,
                            List.of(),
                            null,
                            null,
                            dayMemos,
                            calendarLeaveService.getEntries(date, calendar),
                            calendarLeaveService.getParticipants(calendar),
                            scheduleTypeService.listForCalendar(calendar)
                    );
                });
    }

    public DayDetailResponse save(LocalDate date, ExceptionUpdateRequest request, Calendar calendar) {
        String customCode = normalizeCode(request.customCode(), calendar);
        exceptionService.saveOrUpdate(date, customCode, request.memo(), request.anniversaryMemo(), request.repeatYearly(), calendar);
        return load(date, calendar);
    }

    private DayDetailResponse toResponse(DaySchedule schedule, Calendar calendar) {
        List<MemoDto> dayMemos = dayMemoService.getMemos(schedule.date(), calendar);
        return new DayDetailResponse(
                schedule.date(),
                schedule.baseCode(),
                schedule.effectiveCode(),
                scheduleTypeService.resolveLabel(calendar, schedule.effectiveCode()),
                scheduleTypeService.resolveTimeRange(calendar, schedule.effectiveCode()),
                schedule.memo(),
                schedule.anniversaryMemo(),
                schedule.repeatYearly(),
                schedule.yearlyMemos(),
                schedule.author(),
                schedule.updatedAt(),
                dayMemos,
                calendarLeaveService.getEntries(schedule.date(), calendar),
                calendarLeaveService.getParticipants(calendar),
                scheduleTypeService.listForCalendar(calendar)
        );
    }

    private String normalizeCode(String code, Calendar calendar) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        if (!scheduleTypeService.supportsCode(calendar, normalized)) {
            throw new IllegalArgumentException("invalid_shift_code");
        }
        return normalized;
    }
}
