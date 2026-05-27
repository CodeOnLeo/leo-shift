package io.github.codeonleo.leoshift.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LeaveEntrySaveRequest(
        @NotNull Long userId,
        @NotBlank String leaveType
) {
}
