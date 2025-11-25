package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.dto.CalendarSummaryResponse;
import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarShare;
import io.github.codeonleo.leoshift.entity.User;
import io.github.codeonleo.leoshift.entity.UserSettings;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.CalendarShareRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarAccessService {

    private final SettingsService settingsService;
    private final CalendarRepository calendarRepository;
    private final CalendarShareRepository calendarShareRepository;
    private final UserRepository userRepository;

    public CalendarAccess requireView(Long calendarId) {
        User currentUser = getCurrentUser();
        Calendar calendar = resolveCalendar(calendarId, currentUser);
        ensureViewPermission(calendar, currentUser);
        return new CalendarAccess(calendar, canEdit(calendar, currentUser));
    }

    public CalendarAccess requireEdit(Long calendarId) {
        User currentUser = getCurrentUser();
        Calendar calendar = resolveCalendar(calendarId, currentUser);
        ensureEditPermission(calendar, currentUser);
        return new CalendarAccess(calendar, true);
    }

    public Calendar requireOwner(Long calendarId) {
        User currentUser = getCurrentUser();
        Calendar calendar = resolveCalendar(calendarId, currentUser);
        if (!isOwner(calendar, currentUser)) {
            throw new IllegalArgumentException("calendar_owner_only");
        }
        return calendar;
    }

    public List<CalendarSummaryResponse> listAccessible() {
        User currentUser = getCurrentUser();
        List<CalendarSummaryResponse> result = new ArrayList<>();

        // 내가 소유한 캘린더
        for (Calendar calendar : calendarRepository.findByOwner(currentUser)) {
            result.add(new CalendarSummaryResponse(
                    calendar.getId(),
                    calendar.getName(),
                    currentUser.getName(),
                    true,
                    CalendarShare.Permission.EDIT,
                    CalendarShare.ShareStatus.ACCEPTED,
                    true
            ));
        }

        // 초대 받은 캘린더
        for (CalendarShare share : calendarShareRepository.findByUser(currentUser)) {
            Calendar calendar = share.getCalendar();
            boolean editable = share.getStatus() == CalendarShare.ShareStatus.ACCEPTED
                    && share.getPermission() == CalendarShare.Permission.EDIT;
            result.add(new CalendarSummaryResponse(
                    calendar.getId(),
                    calendar.getName(),
                    calendar.getOwner().getName(),
                    false,
                    share.getPermission(),
                    share.getStatus(),
                    editable
            ));
        }
        return result;
    }

    private Calendar resolveCalendar(Long calendarId, User currentUser) {
        if (calendarId == null) {
            return resolveDefaultCalendar(currentUser);
        }
        return calendarRepository.findById(calendarId)
                .orElseThrow(() -> new IllegalArgumentException("calendar_not_found"));
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
            throw new IllegalStateException("default_calendar_not_set");
        }
        ensureViewPermission(defaultCalendar, currentUser);
        return defaultCalendar;
    }

    private void ensureViewPermission(Calendar calendar, User currentUser) {
        if (isOwner(calendar, currentUser)) {
            return;
        }
        CalendarShare share = calendarShareRepository.findByCalendarAndUser(calendar, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("calendar_access_denied"));
        if (share.getStatus() != CalendarShare.ShareStatus.ACCEPTED) {
            throw new IllegalArgumentException("calendar_access_denied");
        }
    }

    private void ensureEditPermission(Calendar calendar, User currentUser) {
        if (isOwner(calendar, currentUser)) {
            return;
        }
        CalendarShare share = calendarShareRepository.findByCalendarAndUser(calendar, currentUser)
                .orElseThrow(() -> new IllegalArgumentException("calendar_access_denied"));
        if (share.getStatus() != CalendarShare.ShareStatus.ACCEPTED || share.getPermission() != CalendarShare.Permission.EDIT) {
            throw new IllegalArgumentException("calendar_edit_not_allowed");
        }
    }

    private boolean canEdit(Calendar calendar, User currentUser) {
        if (isOwner(calendar, currentUser)) {
            return true;
        }
        return calendarShareRepository.findByCalendarAndUser(calendar, currentUser)
                .filter(share -> share.getStatus() == CalendarShare.ShareStatus.ACCEPTED)
                .map(share -> share.getPermission() == CalendarShare.Permission.EDIT)
                .orElse(false);
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
