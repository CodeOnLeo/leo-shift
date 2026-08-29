package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.calendar.CalendarShare;
import io.github.codeonleo.leoshift.domain.group.Group;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.CalendarShareRepository;
import io.github.codeonleo.leoshift.repository.GroupRepository;
import io.github.codeonleo.leoshift.security.CurrentUser;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캘린더 접근 권한.
 *
 * <p>이전 구현은 공유가 세 벌(shares · grants · groups)로 나뉘고 권한이 합집합으로
 * 더해지기만 해서, 거절한 초대가 무효화되고 특정 개인을 보기 전용으로 낮출 수
 * 없었다. 여기서는 공유가 한 벌이고 {@code ACCEPTED}만 유효하다.
 */
@Service
public class CalendarAccessService {

    private final CalendarRepository calendarRepository;
    private final CalendarShareRepository shareRepository;
    private final GroupRepository groupRepository;
    private final CurrentUser currentUser;

    public CalendarAccessService(CalendarRepository calendarRepository,
                                 CalendarShareRepository shareRepository,
                                 GroupRepository groupRepository,
                                 CurrentUser currentUser) {
        this.calendarRepository = calendarRepository;
        this.shareRepository = shareRepository;
        this.groupRepository = groupRepository;
        this.currentUser = currentUser;
    }

    /** 캘린더 하나에 대한 접근 결과. */
    public record Access(Calendar calendar, CalendarShare.Permission permission,
                         CalendarShare.Visibility visibility, boolean owner) {

        public boolean canEdit() {
            return owner || permission == CalendarShare.Permission.EDIT;
        }
    }

    @Transactional(readOnly = true)
    public Access requireView(Long calendarId) {
        Calendar calendar = calendarRepository.findActiveById(calendarId)
                .orElseThrow(() -> new NotFoundException("캘린더를 찾을 수 없습니다"));
        return resolve(calendar).orElseThrow(() -> new AccessDeniedException("이 캘린더를 볼 권한이 없습니다"));
    }

    @Transactional(readOnly = true)
    public Access requireEdit(Long calendarId) {
        Access access = requireView(calendarId);
        if (!access.canEdit()) {
            throw new AccessDeniedException("이 캘린더를 편집할 권한이 없습니다");
        }
        return access;
    }

    /** 내가 볼 수 있는 캘린더 전부. 내 것 + 수락한 공유. */
    @Transactional(readOnly = true)
    public List<Access> listVisible() {
        Long userId = currentUser.id();
        Map<Long, Access> byCalendarId = new LinkedHashMap<>();

        for (Calendar calendar : calendarRepository.findOwnedBy(userId)) {
            byCalendarId.put(calendar.getId(),
                    new Access(calendar, CalendarShare.Permission.EDIT, CalendarShare.Visibility.FULL, true));
        }
        for (CalendarShare share : shareRepository.findAcceptedForUser(userId)) {
            addShared(byCalendarId, share);
        }
        List<Long> groupIds = currentGroupIds(userId);
        if (!groupIds.isEmpty()) {
            for (CalendarShare share : shareRepository.findAcceptedForGroups(groupIds)) {
                addShared(byCalendarId, share);
            }
        }
        return List.copyOf(byCalendarId.values());
    }

    private void addShared(Map<Long, Access> target, CalendarShare share) {
        Calendar calendar = share.getCalendar();
        if (calendar.getDeletedAt() != null || target.containsKey(calendar.getId())) {
            return;
        }
        target.put(calendar.getId(),
                new Access(calendar, share.getPermission(), share.getVisibility(), false));
    }

    private Optional<Access> resolve(Calendar calendar) {
        Long userId = currentUser.id();

        if (calendar.getOwnerUser() != null && userId.equals(calendar.getOwnerUser().getId())) {
            return Optional.of(new Access(calendar, CalendarShare.Permission.EDIT,
                    CalendarShare.Visibility.FULL, true));
        }

        Optional<CalendarShare> direct = shareRepository.findAcceptedFor(calendar.getId(), userId);
        if (direct.isPresent()) {
            CalendarShare share = direct.get();
            return Optional.of(new Access(calendar, share.getPermission(), share.getVisibility(), false));
        }

        List<Long> groupIds = currentGroupIds(userId);
        if (groupIds.isEmpty()) {
            return Optional.empty();
        }
        return shareRepository.findAcceptedFor(calendar.getId(), groupIds).stream()
                .findFirst()
                .map(share -> new Access(calendar, share.getPermission(), share.getVisibility(), false));
    }

    /** 오늘 기준으로 내가 속한 그룹. 멤버십에 기간이 있으므로 "지금"을 명시해야 한다. */
    private List<Long> currentGroupIds(Long userId) {
        return groupRepository.findByMemberOn(userId, LocalDate.now()).stream()
                .map(Group::getId)
                .toList();
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) {
            super(message);
        }
    }
}
