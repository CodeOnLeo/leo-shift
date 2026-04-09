package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record ScheduleTypeUpdateItemRequest(
        @Size(max = 32)
        String originalCode,

        @NotBlank
        @Size(max = 32)
        String code,

        @NotBlank
        @Size(max = 100)
        String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex code like #2563EB")
        String color,

        LocalTime startTime,
        LocalTime endTime
) {
}
