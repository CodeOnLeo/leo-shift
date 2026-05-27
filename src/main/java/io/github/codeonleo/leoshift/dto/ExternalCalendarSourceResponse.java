package io.github.codeonleo.leoshift.dto;

import java.time.LocalDateTime;

public record ExternalCalendarSourceResponse(
        Long id,
        String name,
        String color,
        String displayMode,
        String dateTextColor,
        String borderColor,
        boolean active,
        LocalDateTime lastSyncedAt,
        String lastError
) {
}
