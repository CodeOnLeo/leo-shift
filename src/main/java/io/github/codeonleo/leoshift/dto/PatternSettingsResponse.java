package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;
import java.util.List;

public record PatternSettingsResponse(
        boolean configured,
        List<String> pattern,
        LocalDate patternStartDate,
        Integer defaultNotificationMinutes
) {
}
