package io.github.codeonleo.leoshift.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record WeeklyRuleUpdateRequest(
        @NotEmpty List<@Valid WeeklyRuleUpdateItemRequest> rules
) {
}
