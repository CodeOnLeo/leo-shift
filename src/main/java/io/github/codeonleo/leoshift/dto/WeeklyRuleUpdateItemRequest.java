package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record WeeklyRuleUpdateItemRequest(
        @Min(1) @Max(7) int dayOfWeek,
        @NotBlank String scheduleTypeCode
) {
}
