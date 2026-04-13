package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.CalendarShareGrantResponse;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarShare;
import io.github.codeonleo.leoshift.entity.CalendarShareGrant;
import io.github.codeonleo.leoshift.entity.ShareGroup;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.repository.CalendarShareGrantRepository;
import io.github.codeonleo.leoshift.repository.ShareGroupRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CalendarGrantService {

    private final CalendarAccessService calendarAccessService;
    private final CalendarShareGrantRepository calendarShareGrantRepository;
    private final ShareGroupRepository shareGroupRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<CalendarShareGrantResponse> listGrants(Long calendarId) {
        Calendar calendar = calendarAccessService.requireOwner(calendarId);
        return calendarShareGrantRepository.findByCalendar(calendar).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CalendarShareGrantResponse grantUser(Long calendarId, String email, CalendarShare.Permission permission) {
        Calendar calendar = calendarAccessService.requireOwner(calendarId);
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("email_required");
        }
        User targetUser = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        if (calendar.getOwner().getId().equals(targetUser.getId())) {
            throw new IllegalArgumentException("cannot_share_with_owner");
        }

        CalendarShareGrant grant = calendarShareGrantRepository.findByCalendarAndTargetUser(calendar, targetUser)
                .orElseGet(() -> CalendarShareGrant.builder()
                        .calendar(calendar)
                        .targetType(CalendarShareGrant.TargetType.USER)
                        .targetUser(targetUser)
                        .build());
        grant.setPermission(resolvePermission(permission));
        grant.setTargetGroup(null);
        return toResponse(calendarShareGrantRepository.save(grant));
    }

    @Transactional
    public void revokeUser(Long calendarId, Long userId) {
        Calendar calendar = calendarAccessService.requireOwner(calendarId);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
        CalendarShareGrant grant = calendarShareGrantRepository.findByCalendarAndTargetUser(calendar, targetUser)
                .orElseThrow(() -> new IllegalArgumentException("calendar_grant_not_found"));
        calendarShareGrantRepository.delete(grant);
    }

    @Transactional
    public CalendarShareGrantResponse grantGroup(Long calendarId, Long groupId, CalendarShare.Permission permission) {
        Calendar calendar = calendarAccessService.requireOwner(calendarId);
        User currentUser = calendarAccessService.getCurrentUser();
        ShareGroup group = shareGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("share_group_not_found"));
        if (group.getOwner() == null || !group.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("share_group_owner_only");
        }

        CalendarShareGrant grant = calendarShareGrantRepository.findByCalendarAndTargetGroup(calendar, group)
                .orElseGet(() -> CalendarShareGrant.builder()
                        .calendar(calendar)
                        .targetType(CalendarShareGrant.TargetType.GROUP)
                        .targetGroup(group)
                        .build());
        grant.setPermission(resolvePermission(permission));
        grant.setTargetUser(null);
        return toResponse(calendarShareGrantRepository.save(grant));
    }

    @Transactional
    public void revokeGroup(Long calendarId, Long groupId) {
        Calendar calendar = calendarAccessService.requireOwner(calendarId);
        User currentUser = calendarAccessService.getCurrentUser();
        ShareGroup group = shareGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("share_group_not_found"));
        if (group.getOwner() == null || !group.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalArgumentException("share_group_owner_only");
        }

        CalendarShareGrant grant = calendarShareGrantRepository.findByCalendarAndTargetGroup(calendar, group)
                .orElseThrow(() -> new IllegalArgumentException("calendar_grant_not_found"));
        calendarShareGrantRepository.delete(grant);
    }

    private CalendarShare.Permission resolvePermission(CalendarShare.Permission permission) {
        return permission != null ? permission : CalendarShare.Permission.VIEW;
    }

    private CalendarShareGrantResponse toResponse(CalendarShareGrant grant) {
        Long targetId;
        String targetName;
        String targetEmail;
        if (grant.getTargetType() == CalendarShareGrant.TargetType.USER && grant.getTargetUser() != null) {
            targetId = grant.getTargetUser().getId();
            targetName = grant.getTargetUser().getName();
            targetEmail = grant.getTargetUser().getEmail();
        } else if (grant.getTargetGroup() != null) {
            targetId = grant.getTargetGroup().getId();
            targetName = grant.getTargetGroup().getName();
            targetEmail = null;
        } else {
            targetId = null;
            targetName = null;
            targetEmail = null;
        }
        return new CalendarShareGrantResponse(
                grant.getId(),
                grant.getTargetType(),
                targetId,
                targetName,
                targetEmail,
                grant.getPermission(),
                grant.getCreatedAt(),
                grant.getUpdatedAt()
        );
    }
}
