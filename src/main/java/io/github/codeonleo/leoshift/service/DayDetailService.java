package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.DayDetailResponse;
import io.github.codeonleo.leoshift.dto.ExceptionUpdateRequest;
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

    public DayDetailResponse load(LocalDate date, Calendar calendar) {
        return scheduleService.resolveDay(date, calendar)
                .map(this::toResponse)
                .orElseGet(() -> new DayDetailResponse(date, null, null, "", "", null, null, false, List.of()));
    }

    public DayDetailResponse save(LocalDate date, ExceptionUpdateRequest request, Calendar calendar) {
        String customCode = normalizeCode(request.customCode());
        exceptionService.saveOrUpdate(date, customCode, request.memo(), request.anniversaryMemo(), request.repeatYearly(), calendar);
        return load(date, calendar);
    }

    private DayDetailResponse toResponse(DaySchedule schedule) {
        ShiftCodeDefinition definition = ShiftCodeDefinition.fromCode(schedule.effectiveCode());
        return new DayDetailResponse(
                schedule.date(),
                schedule.baseCode(),
                schedule.effectiveCode(),
                definition.label(),
                definition.timeRangeLabel(),
                schedule.memo(),
                schedule.anniversaryMemo(),
                schedule.repeatYearly(),
                schedule.yearlyMemos()
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
