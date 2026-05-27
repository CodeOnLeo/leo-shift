package io.github.codeonleo.leoshift.dto;

import java.time.LocalDateTime;

public record ExternalCalendarSourceResponse(
        Long id,
        String name,
        String color,
        boolean active,
        LocalDateTime lastSyncedAt,
        String lastError
) {
}
