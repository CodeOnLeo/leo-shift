package io.github.codeonleo.leoshift.dto;

import io.github.codeonleo.leoshift.entity.CalendarShare;

public record CalendarSummaryResponse(
        Long id,
        String name,
        String ownerName,
        boolean owned,
        CalendarShare.Permission permission,
        CalendarShare.ShareStatus status,
        boolean editable
) {
}
