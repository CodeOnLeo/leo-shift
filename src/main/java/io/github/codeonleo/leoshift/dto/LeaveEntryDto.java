package io.github.codeonleo.leoshift.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveEntryDto(
        Long id,
        LocalDate date,
        String leaveType,
        String leaveLabel,
        String leaveBadge,
        CalendarParticipantDto user,
        CalendarParticipantDto createdBy,
        boolean ownEntry,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
