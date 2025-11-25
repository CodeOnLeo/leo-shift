package io.github.codeonleo.leoshift.dto;

import io.github.codeonleo.leoshift.entity.CalendarShare;
import java.time.LocalDateTime;

public record CalendarShareResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        CalendarShare.Permission permission,
        CalendarShare.ShareStatus status,
        LocalDateTime sharedAt,
        LocalDateTime respondedAt
) {
}
