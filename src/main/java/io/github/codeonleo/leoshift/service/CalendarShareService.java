package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.CalendarShareRequest;
import io.github.codeonleo.leoshift.dto.CalendarShareResponse;
import io.github.codeonleo.leoshift.dto.ShareDecisionRequest;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarShare;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.CalendarShareRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CalendarShareService {

    private final CalendarShareRepository calendarShareRepository;
    private final CalendarAccessService calendarAccessService;
    private final CalendarRepository calendarRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;

    @Transactional
    public CalendarShareResponse invite(Long calendarId, CalendarShareRequest request) {
        Calendar calendar = calendarAccessService.requireOwner(calendarId);
        if (request == null || !StringUtils.hasText(request.email())) {
            throw new IllegalArgumentException("email_required");
        }
        CalendarShare.Permission permission = request.permission() != null ? request.permission() : CalendarShare.Permission.VIEW;
        User targetUser = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        if (calendar.getOwner().getId().equals(targetUser.getId())) {
            throw new IllegalArgumentException("cannot_share_with_owner");
        }

        CalendarShare share = calendarShareRepository.findByCalendarAndUser(calendar, targetUser)
                .orElseGet(() -> CalendarShare.builder()
                        .calendar(calendar)
                        .user(targetUser)
                        .status(CalendarShare.ShareStatus.PENDING)
                        .build());
        share.setPermission(permission);
        if (share.getStatus() == CalendarShare.ShareStatus.REJECTED) {
            share.setStatus(CalendarShare.ShareStatus.PENDING);
            share.setRespondedAt(null);
        }

        share = calendarShareRepository.save(share);
        return toResponse(share);
    }

    @Transactional
    public List<CalendarShareResponse> listShares(Long calendarId) {
        Calendar calendar = calendarAccessService.requireOwner(calendarId);
        return calendarShareRepository.findByCalendar(calendar).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CalendarShareResponse respond(Long calendarId, ShareDecisionRequest request) {
        Calendar calendar = calendarRepository.findById(calendarId)
                .orElseThrow(() -> new IllegalArgumentException("calendar_not_found"));
        User currentUser = calendarAccessService.getCurrentUser();
        if (request == null) {
            throw new IllegalArgumentException("share_decision_required");
        }
        CalendarShare share = calendarShareRepository.findByCalendarAndUser(calendar, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("share_not_found"));
        if (share.getStatus() != CalendarShare.ShareStatus.PENDING && request.accept()) {
            // 이미 처리된 공유는 중복 수락/거절 불가
            return toResponse(share);
        }
        share.setStatus(request.accept() ? CalendarShare.ShareStatus.ACCEPTED : CalendarShare.ShareStatus.REJECTED);
        share.setRespondedAt(LocalDateTime.now());
        share = calendarShareRepository.save(share);

        if (request.accept()) {
            UserSettings settings = settingsService.getOrCreate();
            if (settings.getDefaultCalendar() == null) {
                settingsService.setDefaultCalendar(calendar);
            }
        }
        return toResponse(share);
    }

    private CalendarShareResponse toResponse(CalendarShare share) {
        return new CalendarShareResponse(
                share.getId(),
                share.getUser().getId(),
                share.getUser().getName(),
                share.getUser().getEmail(),
                share.getPermission(),
                share.getStatus(),
                share.getSharedAt(),
                share.getRespondedAt()
        );
    }
}
