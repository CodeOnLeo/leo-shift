package io.github.codeonleo.leoshift.dto;

import java.util.List;

public record TodayResponse(
        boolean patternConfigured,
        SimpleDayDto today,
        List<SimpleDayDto> upcoming
) {
}
