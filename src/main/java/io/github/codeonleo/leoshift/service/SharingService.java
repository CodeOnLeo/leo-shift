package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.calendar.CalendarShare;
import io.github.codeonleo.leoshift.domain.group.Group;
import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.CalendarShareRepository;
import io.github.codeonleo.leoshift.repository.GroupMemberRepository;
import io.github.codeonleo.leoshift.repository.GroupRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.security.CurrentUser;
import io.github.codeonleo.leoshift.service.CalendarAccessService.AccessDeniedException;
import io.github.codeonleo.leoshift.service.CalendarAccessService.NotFoundException;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.IncomingShareResponse;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.SetShareRequest;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.ShareLevel;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.ShareOverviewResponse;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.ShareTargetResponse;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.TargetType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공유 = 대상 × 공개 단계.
 *
 * <p>저장은 캘린더마다 한 행이지만 <b>사용자가 생각하는 단위는 대상 하나</b>다.
 * "직장에는 근무만, 배우자에게는 전부." 그 번역이 이 서비스가 하는 일의 전부다.
 *
 * <p>"근무만"이 별도의 권한 값이 아니라 <b>근무 캘린더만 공유하는 것</b>이라는 점이
 * 중요하다. 개인 일정은 애초에 나가지 않으므로, 조회 경로 어딘가에서 필터를
 * 빠뜨려도 새어 나갈 데이터가 없다. 이전 구현은 반대로 전부 공유해 놓고 화면에서
 * 가렸다.
 *
 * <p>그룹 공유는 즉시 유효하고, 개인 공유는 상대가 수락해야 유효하다. 그룹은 내가
 * 이미 속한 관계이고, 개인 공유는 상대의 캘린더 목록에 남의 캘린더를 밀어 넣는
 * 일이기 때문이다.
 */
@Service
public class SharingService {

    private final CalendarRepository calendarRepository;
    private final CalendarShareRepository shareRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public SharingService(CalendarRepository calendarRepository,
                          CalendarShareRepository shareRepository,
                          GroupRepository groupRepository,
                          GroupMemberRepository memberRepository,
                          UserRepository userRepository,
                          CurrentUser currentUser) {
        this.calendarRepository = calendarRepository;
        this.shareRepository = shareRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    // ---------------------------------------------------------------- 조회

    /**
     * "지금 누가 내 뭘 보고 있지?"
     *
     * <p>개인 일정까지 담기는 캘린더라면 이 질문에 언제든 답할 수 있어야 한다.
     * 이전 구현에는 공유를 취소하는 기능 자체가 없었다.
     */
    @Transactional(readOnly = true)
    public ShareOverviewResponse overview() {
        Long viewerId = currentUser.id();

        Map<TargetKey, List<CalendarShare>> byTarget = new LinkedHashMap<>();
        for (CalendarShare share : shareRepository.findLiveSharesOfOwner(viewerId)) {
            TargetKey key = keyOf(share);
            if (key == null) {
                continue;
            }
            byTarget.computeIfAbsent(key, k -> new ArrayList<>()).add(share);
        }

        Map<Long, Long> memberCounts = memberCounts(byTarget.keySet().stream()
                .filter(key -> key.type() == TargetType.GROUP)
                .map(TargetKey::id)
                .toList());

        List<ShareTargetResponse> targets = byTarget.entrySet().stream()
                .map(entry -> describe(entry.getKey(), entry.getValue(), memberCounts))
                .toList();

        List<IncomingShareResponse> incoming = shareRepository.findPendingInvitations(viewerId).stream()
                .map(share -> {
                    Calendar calendar = share.getCalendar();
                    User owner = calendar.getOwnerUser();
                    return new IncomingShareResponse(
                            share.getId(), calendar.getId(), calendar.getName(),
                            owner != null ? owner.getName() : "(그룹 캘린더)",
                            owner != null ? owner.getEmail() : null,
                            share.getPermission().name(), share.getVisibility().name());
                })
                .toList();

        List<Calendar> mine = calendarRepository.findOwnedBy(viewerId);
        int work = (int) mine.stream().filter(c -> c.getKind() == Calendar.Kind.WORK).count();

        return new ShareOverviewResponse(targets, incoming, work, mine.size() - work);
    }

    // ---------------------------------------------------------------- 변경

    /**
     * 대상 하나의 공개 단계를 정한다. 이미 공유 중이면 단계만 바뀐다.
     *
     * <p>단계가 바뀌면 <b>내 캘린더 전부를 다시 훑어</b> 포함될 것은 공유하고
     * 빠질 것은 취소한다. 한 대상에 대한 상태를 매번 통째로 다시 만드는 방식이라,
     * 단계를 낮췄을 때 예전 행이 남아 권한이 더해지기만 하던 문제가 생기지 않는다.
     *
     * <p>다만 <b>나중에 만든 캘린더는 기존 공유를 자동으로 따라가지 않는다.</b>
     * 캘린더 관리 화면이 생기면 캘린더 생성 경로에서 이 메서드를 다시 부르면 된다.
     */
    @Transactional
    public ShareTargetResponse setLevel(SetShareRequest request) {
        User me = currentUser.require();
        TargetKey target = resolveTarget(request, me);

        List<Calendar> mine = calendarRepository.findOwnedBy(me.getId());
        if (mine.isEmpty()) {
            throw new IllegalArgumentException("공유할 캘린더가 없습니다");
        }

        List<CalendarShare> live = new ArrayList<>();
        for (Calendar calendar : mine) {
            Optional<CalendarShare> existing = findLive(calendar.getId(), target);

            if (!includes(request.level(), calendar)) {
                existing.ifPresent(share -> share.setStatus(CalendarShare.Status.REVOKED));
                continue;
            }
            live.add(existing
                    .map(share -> refresh(share, target))
                    .orElseGet(() -> create(calendar, target, me)));
        }

        if (live.isEmpty()) {
            // 근무 캘린더가 없는데 "근무만"을 고른 경우. 조용히 아무것도 안 하면 안 된다.
            throw new IllegalArgumentException("이 단계로 공유할 캘린더가 없습니다");
        }

        Map<Long, Long> counts = target.type() == TargetType.GROUP
                ? memberCounts(List.of(target.id()))
                : Map.of();
        return describe(target, live, counts);
    }

    /** 공유를 끊는다. 대상에 걸린 행을 전부 취소한다. */
    @Transactional
    public void revoke(TargetType targetType, Long targetId) {
        Long viewerId = currentUser.id();
        int revoked = 0;

        for (Calendar calendar : calendarRepository.findOwnedBy(viewerId)) {
            Optional<CalendarShare> existing = findLive(calendar.getId(), new TargetKey(targetType, targetId));
            if (existing.isPresent()) {
                existing.get().setStatus(CalendarShare.Status.REVOKED);
                revoked++;
            }
        }
        if (revoked == 0) {
            throw new NotFoundException("공유를 찾을 수 없습니다");
        }
    }

    /** 받은 공유를 수락한다. 그래야 내 캘린더 목록에 들어온다. */
    @Transactional
    public void respond(Long shareId, boolean accept) {
        Long viewerId = currentUser.id();
        CalendarShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new NotFoundException("공유를 찾을 수 없습니다"));

        if (share.getGranteeUser() == null || !share.getGranteeUser().getId().equals(viewerId)) {
            throw new AccessDeniedException("이 공유에 응답할 권한이 없습니다");
        }
        if (share.getStatus() != CalendarShare.Status.PENDING) {
            throw new IllegalArgumentException("이미 응답한 공유입니다");
        }

        share.setStatus(accept ? CalendarShare.Status.ACCEPTED : CalendarShare.Status.REJECTED);
        share.setRespondedAt(Instant.now());
    }

    // -------------------------------------------------------------- 대상 판정

    private record TargetKey(TargetType type, Long id) {
    }

    private TargetKey resolveTarget(SetShareRequest request, User me) {
        if (request.targetType() == TargetType.GROUP) {
            if (request.groupId() == null) {
                throw new IllegalArgumentException("공유할 그룹을 골라 주세요");
            }
            Group group = groupRepository.findActiveById(request.groupId())
                    .orElseThrow(() -> new NotFoundException("그룹을 찾을 수 없습니다"));
            // 내가 속하지 않은 그룹에 내 캘린더를 밀어 넣을 수는 없다.
            boolean belongs = group.getOwner().getId().equals(me.getId())
                    || memberRepository.findActive(group.getId(), me.getId()).isPresent();
            if (!belongs) {
                throw new AccessDeniedException("소속되지 않은 그룹에는 공유할 수 없습니다");
            }
            return new TargetKey(TargetType.GROUP, group.getId());
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("공유할 사람의 이메일을 입력해 주세요");
        }
        User grantee = userRepository.findByEmail(request.email().trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "그 이메일로 가입한 사용자가 없습니다: " + request.email().trim()));
        if (grantee.getId().equals(me.getId())) {
            throw new IllegalArgumentException("자기 자신에게는 공유할 수 없습니다");
        }
        return new TargetKey(TargetType.USER, grantee.getId());
    }

    private TargetKey keyOf(CalendarShare share) {
        if (share.getGranteeGroup() != null) {
            // 지워진 그룹에 걸린 공유는 판정에서도 빠지므로 화면에도 내보내지 않는다.
            return share.getGranteeGroup().getDeletedAt() != null
                    ? null
                    : new TargetKey(TargetType.GROUP, share.getGranteeGroup().getId());
        }
        if (share.getGranteeUser() != null) {
            return new TargetKey(TargetType.USER, share.getGranteeUser().getId());
        }
        return null;
    }

    private Optional<CalendarShare> findLive(Long calendarId, TargetKey target) {
        return target.type() == TargetType.GROUP
                ? shareRepository.findLiveGroupShare(calendarId, target.id())
                : shareRepository.findLiveUserShare(calendarId, target.id());
    }

    // -------------------------------------------------------------- 행 만들기

    /** 단계가 이 캘린더를 포함하는가. "근무만"은 근무 캘린더만 내보낸다. */
    private static boolean includes(ShareLevel level, Calendar calendar) {
        return level == ShareLevel.FULL || calendar.getKind() == Calendar.Kind.WORK;
    }

    private CalendarShare create(Calendar calendar, TargetKey target, User me) {
        CalendarShare.CalendarShareBuilder builder = CalendarShare.builder()
                .calendar(calendar)
                .permission(CalendarShare.Permission.VIEW)
                .visibility(CalendarShare.Visibility.FULL)
                .createdBy(me);

        if (target.type() == TargetType.GROUP) {
            builder.granteeGroup(groupRepository.getReferenceById(target.id()))
                    .status(CalendarShare.Status.ACCEPTED);
        } else {
            builder.granteeUser(userRepository.getReferenceById(target.id()))
                    .status(CalendarShare.Status.PENDING);
        }
        return shareRepository.save(builder.build());
    }

    /**
     * 이미 있는 공유를 현재 단계에 맞춘다.
     *
     * <p>거절당한 개인 공유를 다시 걸면 {@code PENDING}으로 돌아간다. 다시 물어보는
     * 것이지 몰래 되살리는 것이 아니므로, 상대는 여전히 거절할 수 있다.
     */
    private CalendarShare refresh(CalendarShare share, TargetKey target) {
        share.setPermission(CalendarShare.Permission.VIEW);
        share.setVisibility(CalendarShare.Visibility.FULL);

        if (target.type() == TargetType.GROUP) {
            share.setStatus(CalendarShare.Status.ACCEPTED);
        } else if (share.getStatus() == CalendarShare.Status.REJECTED) {
            share.setStatus(CalendarShare.Status.PENDING);
            share.setRespondedAt(null);
        }
        return share;
    }

    // ---------------------------------------------------------------- 요약

    /** 캘린더별 행 여러 개를 화면 한 줄로 접는다. */
    private ShareTargetResponse describe(TargetKey target, List<CalendarShare> shares,
                                         Map<Long, Long> memberCounts) {
        CalendarShare first = shares.get(0);

        String name;
        String email = null;
        if (target.type() == TargetType.GROUP) {
            name = first.getGranteeGroup().getName();
        } else {
            User grantee = first.getGranteeUser();
            name = grantee.getName();
            email = grantee.getEmail();
        }

        // 개인 캘린더가 하나라도 나가면 "전체"다.
        boolean full = shares.stream()
                .anyMatch(share -> share.getCalendar().getKind() != Calendar.Kind.WORK);

        boolean pending = shares.stream()
                .anyMatch(share -> share.getStatus() == CalendarShare.Status.PENDING);
        boolean anyAccepted = shares.stream().anyMatch(CalendarShare::isActive);
        String status = pending ? "PENDING" : anyAccepted ? "ACCEPTED" : "REJECTED";

        return new ShareTargetResponse(
                target.type().name(), target.id(), name, email,
                (full ? ShareLevel.FULL : ShareLevel.WORK_ONLY).name(),
                status, pending,
                memberCounts.getOrDefault(target.id(), 0L),
                shares.size());
    }

    private Map<Long, Long> memberCounts(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : memberRepository.countCurrentMembers(groupIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
