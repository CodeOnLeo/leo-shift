package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.Size;

public record ExternalCalendarDisplayRequest(
        @Size(max = 32) String displayMode,
        @Size(max = 20) String color,
        @Size(max = 20) String dateTextColor,
        @Size(max = 20) String borderColor
) {
}
