package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingsRequest(
        @NotNull @Min(5) @Max(240) Integer minutes
) {
}
