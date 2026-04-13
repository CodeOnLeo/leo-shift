package io.github.codeonleo.leoshift.dto;

import io.github.codeonleo.leoshift.entity.CalendarShare;
import jakarta.validation.constraints.NotNull;

public record CalendarGrantPermissionRequest(
        @NotNull CalendarShare.Permission permission
) {
}
