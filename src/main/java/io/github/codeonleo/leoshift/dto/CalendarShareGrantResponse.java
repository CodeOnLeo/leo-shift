package io.github.codeonleo.leoshift.dto;

import io.github.codeonleo.leoshift.entity.CalendarShare;
import io.github.codeonleo.leoshift.entity.CalendarShareGrant;
import java.time.LocalDateTime;

public record CalendarShareGrantResponse(
        Long id,
        CalendarShareGrant.TargetType targetType,
        Long targetId,
        String targetName,
        String targetEmail,
        CalendarShare.Permission permission,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
