package io.github.codeonleo.leoshift.dto;

public record WeeklyRuleResponse(
        int dayOfWeek,
        String scheduleTypeCode
) {
}
