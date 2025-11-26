package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CalendarCreateRequest(
        @NotBlank(message = "Calendar name is required")
        @Size(min = 1, max = 100, message = "Calendar name must be between 1 and 100 characters")
        String name,

        Boolean patternEnabled
) {
    public CalendarCreateRequest {
        if (patternEnabled == null) {
            patternEnabled = true;
        }
    }
}
