package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record PatternSettingsRequest(
        @Size(min = 1, message = "pattern must have at least one code") List<String> pattern,
        @NotNull LocalDate patternStartDate,
        Integer defaultNotificationMinutes
) {
}
