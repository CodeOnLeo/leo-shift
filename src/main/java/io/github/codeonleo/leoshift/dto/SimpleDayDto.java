package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;
import java.util.List;

public record SimpleDayDto(
        LocalDate date,
        String code,
        String shiftLabel,
        String timeRange,
        List<String> memos
) {
}
