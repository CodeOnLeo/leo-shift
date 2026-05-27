package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExternalCalendarSourceRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String feedUrl,
        @Size(max = 20) String color
) {
}
