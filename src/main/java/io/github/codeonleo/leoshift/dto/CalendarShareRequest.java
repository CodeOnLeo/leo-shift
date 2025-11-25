package io.github.codeonleo.leoshift.dto;

import io.github.codeonleo.leoshift.entity.CalendarShare;

public record CalendarShareRequest(
        String email,
        CalendarShare.Permission permission
) {
}
