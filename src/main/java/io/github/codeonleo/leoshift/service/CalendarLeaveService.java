package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.CalendarParticipantDto;
import io.github.codeonleo.leoshift.dto.LeaveEntryDto;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarLeaveEntry;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.repository.CalendarLeaveEntryRepository;
import io.github.codeonleo.leoshift.util.ColorTagUtil;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CalendarLeaveService {

    private final CalendarLeaveEntryRepository repository;
    private final CalendarAccessService calendarAccessService;

    @Transactional
    public CalendarLeaveEntry saveOrUpdate(LocalDate date, Long targetUserId, String leaveType, Calendar calendar) {
        User currentUser = calendarAccessService.getCurrentUser();
        if (!currentUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("leave_entry_self_only");
        }
        Map<Long, User> participants = calendarAccessService.listParticipants(calendar).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        User targetUser = participants.get(targetUserId);
        if (targetUser == null) {
            throw new IllegalArgumentException("calendar_participant_not_found");
        }

        CalendarLeaveEntry.LeaveType normalizedType = normalizeLeaveType(leaveType);
        CalendarLeaveEntry entry = repository.findByCalendarAndDateAndTargetUser(calendar, date, targetUser)
                .orElseGet(() -> {
                    CalendarLeaveEntry created = new CalendarLeaveEntry();
                    created.setCalendar(calendar);
                    created.setDate(date);
                    created.setTargetUser(targetUser);
                    return created;
                });

        entry.setCreatedByUser(currentUser);
        entry.setLeaveType(normalizedType);
        return repository.save(entry);
    }

    @Transactional
    public void deleteById(LocalDate date, Long leaveEntryId, Calendar calendar) {
        User currentUser = calendarAccessService.getCurrentUser();
        CalendarLeaveEntry entry = repository.findById(leaveEntryId)
                .orElseThrow(() -> new IllegalArgumentException("leave_entry_not_found"));
        if (!entry.getCalendar().getId().equals(calendar.getId()) || !entry.getDate().equals(date)) {
            throw new IllegalArgumentException("leave_entry_not_found");
        }
        if (!entry.getTargetUser().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("leave_entry_self_only");
        }
        repository.delete(entry);
    }

    public List<LeaveEntryDto> getEntries(LocalDate date, Calendar calendar) {
        User currentUser = calendarAccessService.getCurrentUser();
        return repository.findByCalendarAndDate(calendar, date).stream()
                .map(entry -> toDto(entry, currentUser))
                .toList();
    }

    public List<LeaveEntryDto> getEntriesInRange(LocalDate start, LocalDate end, Calendar calendar) {
        User currentUser = calendarAccessService.getCurrentUser();
        return repository.findByCalendarAndDateBetween(calendar, start, end).stream()
                .map(entry -> toDto(entry, currentUser))
                .toList();
    }

    public List<CalendarParticipantDto> getParticipants(Calendar calendar) {
        return calendarAccessService.listParticipants(calendar).stream()
                .map(this::toParticipantDto)
                .toList();
    }

    private LeaveEntryDto toDto(CalendarLeaveEntry entry, User currentUser) {
        return new LeaveEntryDto(
                entry.getId(),
                entry.getDate(),
                entry.getLeaveType().name(),
                leaveLabel(entry.getLeaveType()),
                leaveBadge(entry.getLeaveType()),
                toParticipantDto(entry.getTargetUser()),
                toParticipantDto(entry.getCreatedByUser()),
                entry.getTargetUser().getId().equals(currentUser.getId()),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }

    private CalendarParticipantDto toParticipantDto(User user) {
        return new CalendarParticipantDto(
                user.getId(),
                user.getName(),
                user.getNickname(),
                ColorTagUtil.resolve(user)
        );
    }

    private CalendarLeaveEntry.LeaveType normalizeLeaveType(String leaveType) {
        try {
            return CalendarLeaveEntry.LeaveType.valueOf(leaveType.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid_leave_type");
        }
    }

    private String leaveLabel(CalendarLeaveEntry.LeaveType leaveType) {
        return switch (leaveType) {
            case ANNUAL -> "연차";
            case HALF_AM -> "오전 반차";
            case HALF_PM -> "오후 반차";
        };
    }

    private String leaveBadge(CalendarLeaveEntry.LeaveType leaveType) {
        return switch (leaveType) {
            case ANNUAL -> "연차";
            case HALF_AM -> "AM";
            case HALF_PM -> "PM";
        };
    }
}
