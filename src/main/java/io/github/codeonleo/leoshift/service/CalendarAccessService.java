package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.CalendarSummaryResponse;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarShare;
import io.github.codeonleo.leoshift.entity.CalendarShareGrant;
import io.github.codeonleo.leoshift.entity.ShareGroup;
import io.github.codeonleo.leoshift.entity.ShareGroupMember;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.CalendarShareRepository;
import io.github.codeonleo.leoshift.repository.CalendarShareGrantRepository;
import io.github.codeonleo.leoshift.repository.ShareGroupMemberRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CalendarAccessService {

    private final SettingsService settingsService;
    private final CalendarRepository calendarRepository;
    private final CalendarShareRepository calendarShareRepository;
    private final CalendarShareGrantRepository calendarShareGrantRepository;
    private final ShareGroupMemberRepository shareGroupMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public CalendarAccess requireView(Long calendarId) {
        User currentUser = getCurrentUser();
        Calendar calendar = resolveCalendar(calendarId, currentUser);
        ensureViewPermission(calendar, currentUser);
        return new CalendarAccess(calendar, canEdit(calendar, currentUser));
    }

    @Transactional
    public CalendarAccess requireEdit(Long calendarId) {
        User currentUser = getCurrentUser();
        Calendar calendar = resolveCalendar(calendarId, currentUser);
        ensureEditPermission(calendar, currentUser);
        return new CalendarAccess(calendar, true);
    }

    @Transactional
    public Calendar requireOwner(Long calendarId) {
        User currentUser = getCurrentUser();
        Calendar calendar = resolveCalendar(calendarId, currentUser);
        if (!isOwner(calendar, currentUser)) {
            throw new IllegalArgumentException("calendar_owner_only");
        }
        return calendar;
    }

    @Transactional(readOnly = true)
    public List<CalendarSummaryResponse> listAccessible() {
        User currentUser = getCurrentUser();
        Map<Long, CalendarSummaryResponse> result = new LinkedHashMap<>();

        for (Calendar calendar : calendarRepository.findByOwner(currentUser)) {
            result.put(calendar.getId(), new CalendarSummaryResponse(
                    calendar.getId(),
                    calendar.getName(),
                    currentUser.getName(),
                    true,
                    CalendarShare.Permission.EDIT,
                    CalendarShare.ShareStatus.ACCEPTED,
                    true,
                    calendar.isPatternEnabled()
            ));
        }

        for (CalendarShare share : calendarShareRepository.findByUser(currentUser)) {
            Calendar calendar = share.getCalendar();
            boolean editable = share.getStatus() == CalendarShare.ShareStatus.ACCEPTED
                    && share.getPermission() == CalendarShare.Permission.EDIT;
            mergeSharedCalendar(result, new CalendarSummaryResponse(
                    calendar.getId(),
                    calendar.getName(),
                    calendar.getOwner().getName(),
                    false,
                    share.getPermission(),
                    share.getStatus(),
                    editable,
                    calendar.isPatternEnabled()
            ));
        }

        List<ShareGroup> groups = shareGroupMemberRepository.findByUser(currentUser).stream()
                .map(ShareGroupMember::getGroup)
                .distinct()
                .toList();

        for (CalendarShareGrant grant : calendarShareGrantRepository.findByTargetUser(currentUser)) {
            Calendar calendar = grant.getCalendar();
            mergeSharedCalendar(result, new CalendarSummaryResponse(
                    calendar.getId(),
                    calendar.getName(),
                    calendar.getOwner().getName(),
                    false,
                    grant.getPermission(),
                    CalendarShare.ShareStatus.ACCEPTED,
                    grant.getPermission() == CalendarShare.Permission.EDIT,
                    calendar.isPatternEnabled()
            ));
        }

        if (!groups.isEmpty()) {
            for (CalendarShareGrant grant : calendarShareGrantRepository.findByTargetGroupIn(groups)) {
                Calendar calendar = grant.getCalendar();
                mergeSharedCalendar(result, new CalendarSummaryResponse(
                        calendar.getId(),
                        calendar.getName(),
                        calendar.getOwner().getName(),
                        false,
                        grant.getPermission(),
                        CalendarShare.ShareStatus.ACCEPTED,
                        grant.getPermission() == CalendarShare.Permission.EDIT,
                        calendar.isPatternEnabled()
                ));
            }
        }

        return result.values().stream()
                .sorted(Comparator.comparing(CalendarSummaryResponse::owned).reversed()
                        .thenComparing(CalendarSummaryResponse::ownerName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(CalendarSummaryResponse::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private Calendar resolveCalendar(Long calendarId, User currentUser) {
        if (calendarId == null) {
            return resolveDefaultCalendar(currentUser);
        }
        Calendar calendar = calendarRepository.findById(calendarId)
                .orElseThrow(() -> new IllegalArgumentException("calendar_not_found"));
        // Ensure owner is initialized within transaction
        if (calendar.getOwner() != null) {
            calendar.getOwner().getId();
        }
        return calendar;
    }

    private Calendar resolveDefaultCalendar(User currentUser) {
        UserSettings settings = settingsService.getOrCreate();
        Calendar defaultCalendar = settings.getDefaultCalendar();
        if (defaultCalendar == null) {
            // 기존 사용자 중 defaultCalendar가 비어 있는 경우 소유한 첫 캘린더를 기본으로 설정
            List<Calendar> owned = calendarRepository.findByOwner(currentUser);
            if (!owned.isEmpty()) {
                defaultCalendar = owned.getFirst();
                settingsService.setDefaultCalendar(defaultCalendar);
            }
        }
        if (defaultCalendar == null) {
            List<CalendarSummaryResponse> accessible = listAccessible().stream()
                    .filter(calendar -> !calendar.owned() && calendar.status() == CalendarShare.ShareStatus.ACCEPTED)
                    .toList();
            if (!accessible.isEmpty()) {
                defaultCalendar = calendarRepository.findById(accessible.getFirst().id()).orElse(null);
                if (defaultCalendar != null) {
                    settingsService.setDefaultCalendar(defaultCalendar);
                }
            }
        }
        if (defaultCalendar == null) {
            throw new IllegalStateException("default_calendar_not_set");
        }
        if (defaultCalendar.getOwner() != null) {
            defaultCalendar.getOwner().getId();
        }
        ensureViewPermission(defaultCalendar, currentUser);
        return defaultCalendar;
    }

    private void ensureViewPermission(Calendar calendar, User currentUser) {
        if (isOwner(calendar, currentUser)) {
            return;
        }
        if (resolveSharedPermission(calendar, currentUser).isEmpty()) {
            throw new IllegalArgumentException("calendar_access_denied");
        }
    }

    private void ensureEditPermission(Calendar calendar, User currentUser) {
        if (isOwner(calendar, currentUser)) {
            return;
        }
        Optional<CalendarShare.Permission> permission = resolveSharedPermission(calendar, currentUser);
        if (permission.isEmpty() || permission.get() != CalendarShare.Permission.EDIT) {
            throw new IllegalArgumentException("calendar_edit_not_allowed");
        }
    }

    private boolean canEdit(Calendar calendar, User currentUser) {
        if (isOwner(calendar, currentUser)) {
            return true;
        }
        return resolveSharedPermission(calendar, currentUser)
                .map(permission -> permission == CalendarShare.Permission.EDIT)
                .orElse(false);
    }

    private Optional<CalendarShare.Permission> resolveSharedPermission(Calendar calendar, User currentUser) {
        CalendarShare.Permission effectivePermission = null;

        Optional<CalendarShare> directShare = calendarShareRepository.findByCalendarAndUser(calendar, currentUser)
                .filter(share -> share.getStatus() == CalendarShare.ShareStatus.ACCEPTED);
        if (directShare.isPresent()) {
            effectivePermission = maxPermission(effectivePermission, directShare.get().getPermission());
        }

        Optional<CalendarShareGrant> directGrant = calendarShareGrantRepository.findByCalendarAndTargetUser(calendar, currentUser);
        if (directGrant.isPresent()) {
            effectivePermission = maxPermission(effectivePermission, directGrant.get().getPermission());
        }

        List<ShareGroup> groups = shareGroupMemberRepository.findByUser(currentUser).stream()
                .map(ShareGroupMember::getGroup)
                .toList();
        if (!groups.isEmpty()) {
            for (CalendarShareGrant grant : calendarShareGrantRepository.findByTargetGroupIn(groups)) {
                if (grant.getCalendar() != null && grant.getCalendar().getId().equals(calendar.getId())) {
                    effectivePermission = maxPermission(effectivePermission, grant.getPermission());
                }
            }
        }

        return Optional.ofNullable(effectivePermission);
    }

    private CalendarShare.Permission maxPermission(CalendarShare.Permission left, CalendarShare.Permission right) {
        if (left == CalendarShare.Permission.EDIT || right == CalendarShare.Permission.EDIT) {
            return CalendarShare.Permission.EDIT;
        }
        return left != null ? left : right;
    }

    private void mergeSharedCalendar(Map<Long, CalendarSummaryResponse> result, CalendarSummaryResponse candidate) {
        CalendarSummaryResponse existing = result.get(candidate.id());
        if (existing == null || (!existing.owned() && isCandidateHigher(existing, candidate))) {
            result.put(candidate.id(), candidate);
        }
    }

    private boolean isCandidateHigher(CalendarSummaryResponse existing, CalendarSummaryResponse candidate) {
        return rank(candidate) > rank(existing);
    }

    private int rank(CalendarSummaryResponse response) {
        int rank = 0;
        if (response.status() == CalendarShare.ShareStatus.ACCEPTED) {
            rank += 10;
        }
        if (response.permission() == CalendarShare.Permission.EDIT) {
            rank += 1;
        }
        return rank;
    }

    private boolean isOwner(Calendar calendar, User currentUser) {
        return calendar.getOwner() != null && calendar.getOwner().getId().equals(currentUser.getId());
    }

    public User getCurrentUser() {
        Long currentUserId = settingsService.currentUserId();
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));
    }

    public record CalendarAccess(Calendar calendar, boolean editable) {
    }
}
