package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.Size;

public record CalendarUpdateRequest(
        @Size(min = 1, max = 100, message = "Calendar name must be between 1 and 100 characters")
        String name,
        Boolean patternEnabled
) {
}
