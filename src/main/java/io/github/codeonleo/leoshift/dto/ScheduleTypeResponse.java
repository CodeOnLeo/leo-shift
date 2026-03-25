package io.github.codeonleo.leoshift.dto;

import java.time.LocalTime;

public record ScheduleTypeResponse(
        String code,
        String name,
        String color,
        LocalTime startTime,
        LocalTime endTime,
        String timeRangeLabel,
        boolean countsAsWork,
        boolean defaultOff
) {
}
