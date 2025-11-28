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

    private static final List<String> ALLOWED_CODES = List.of("D", "A", "N", "O");

    private final ScheduleService scheduleService;
    private final ExceptionService exceptionService;
    private final DayMemoService dayMemoService;

    public DayDetailResponse load(LocalDate date, Calendar calendar) {
        return scheduleService.resolveDay(date, calendar)
                .map(schedule -> toResponse(schedule, calendar))
                .orElseGet(() -> {
                    List<MemoDto> dayMemos = dayMemoService.getMemos(date, calendar);
                    return new DayDetailResponse(date, null, null, "", "", null, null, false, List.of(), null, null, dayMemos);
                });
    }

    public DayDetailResponse save(LocalDate date, ExceptionUpdateRequest request, Calendar calendar) {
        String customCode = normalizeCode(request.customCode());
        exceptionService.saveOrUpdate(date, customCode, request.memo(), request.anniversaryMemo(), request.repeatYearly(), calendar);
        return load(date, calendar);
    }

    private DayDetailResponse toResponse(DaySchedule schedule, Calendar calendar) {
        ShiftCodeDefinition definition = ShiftCodeDefinition.fromCode(schedule.effectiveCode());
        List<MemoDto> dayMemos = dayMemoService.getMemos(schedule.date(), calendar);
        return new DayDetailResponse(
                schedule.date(),
                schedule.baseCode(),
                schedule.effectiveCode(),
                definition.label(),
                definition.timeRangeLabel(),
                schedule.memo(),
                schedule.anniversaryMemo(),
                schedule.repeatYearly(),
                schedule.yearlyMemos(),
                schedule.author(),
                schedule.updatedAt(),
                dayMemos
        );
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        if (!ALLOWED_CODES.contains(normalized)) {
            throw new IllegalArgumentException("invalid_shift_code");
        }
        return normalized;
    }
}
