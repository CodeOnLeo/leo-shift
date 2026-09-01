package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.CalendarShare;
import io.github.codeonleo.leoshift.domain.group.Group;
import io.github.codeonleo.leoshift.domain.group.GroupMember;
import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.repository.CalendarShareRepository;
import io.github.codeonleo.leoshift.repository.GroupMemberRepository;
import io.github.codeonleo.leoshift.repository.GroupRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.security.CurrentUser;
import io.github.codeonleo.leoshift.service.CalendarAccessService.AccessDeniedException;
import io.github.codeonleo.leoshift.service.CalendarAccessService.NotFoundException;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.AddMemberRequest;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.GroupDetailResponse;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.GroupSummaryResponse;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.MemberResponse;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.SaveGroupRequest;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.UpdateMemberRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹과 멤버십.
 *
 * <p><b>그룹에 넣는 것과 일정이 보이는 것은 별개다.</b> 그룹은 데이터를 담지
 * 않으므로, 누군가를 그룹에 추가해도 그 사람의 일정은 보이지 않는다. 각자가
 * 자기 캘린더를 그 그룹에 공유해야 보인다({@link SharingService}).
 *
 * <p>그래서 초대 수락 절차가 없다. 그룹 추가만으로는 아무것도 노출되지 않기
 * 때문이다. 이전 구현은 그룹 참여 자체가 권한이어서 승인 절차가 필요했고,
 * 그러면서도 거절한 초대가 권한 합집합에 남아 무효화되지 않았다.
 *
 * <p>멤버 관리는 그룹 소유자만 한다. 다만 <b>나가는 것은 본인이 한다</b> —
 * 이전 구현에서 멤버가 스스로 나갈 수 없었던 것이 실제 불편이었다.
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final CalendarShareRepository shareRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository memberRepository,
                        CalendarShareRepository shareRepository,
                        UserRepository userRepository,
                        CurrentUser currentUser) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.shareRepository = shareRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    // ---------------------------------------------------------------- 조회

    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> list() {
        Long viewerId = currentUser.id();
        List<Group> groups = groupRepository.findVisibleTo(viewerId);
        if (groups.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> counts = countMembers(groups.stream().map(Group::getId).toList());
        return groups.stream()
                .map(group -> GroupSummaryResponse.from(
                        group,
                        counts.getOrDefault(group.getId(), 0L),
                        isOwner(group, viewerId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse detail(Long groupId) {
        Long viewerId = currentUser.id();
        Group group = requireVisible(groupId, viewerId);

        List<GroupMember> members = memberRepository.findAllOf(groupId);
        Set<Long> sharedUserIds = sharedUserIds(groupId, members);

        return new GroupDetailResponse(
                group.getId(), group.getName(), group.getKind().name(),
                group.getColor(), group.getDescription(),
                isOwner(group, viewerId),
                members.stream()
                        .map(member -> MemberResponse.from(
                                member, viewerId, sharedUserIds.contains(member.getUser().getId())))
                        .toList());
    }

    // ---------------------------------------------------------------- 그룹

    @Transactional
    public GroupSummaryResponse create(SaveGroupRequest request) {
        User owner = currentUser.require();

        Group group = groupRepository.save(Group.builder()
                .owner(owner)
                .name(request.name().trim())
                .kind(request.kind())
                .description(blankToNull(request.description()))
                .color(request.color())
                .build());

        // 소유자도 멤버다. 그러지 않으면 자기가 만든 그룹의 타임라인에 자기가 없다.
        memberRepository.save(GroupMember.builder()
                .group(group)
                .user(owner)
                .role(GroupMember.Role.OWNER)
                .joinedOn(LocalDate.now())
                .build());

        return GroupSummaryResponse.from(group, 1L, true);
    }

    @Transactional
    public GroupSummaryResponse update(Long groupId, SaveGroupRequest request) {
        Group group = requireOwner(groupId);

        group.setName(request.name().trim());
        group.setKind(request.kind());
        group.setDescription(blankToNull(request.description()));
        group.setColor(request.color());

        long memberCount = countMembers(List.of(groupId)).getOrDefault(groupId, 0L);
        return GroupSummaryResponse.from(group, memberCount, true);
    }

    /**
     * 그룹을 지운다. 소유자만.
     *
     * <p>멤버십과 공유 기록은 남긴다. 프로젝트 기록을 되살릴 여지를 두고,
     * 무엇보다 <b>남의 캘린더에 걸린 공유를 지우는 일</b>이므로 되돌릴 수 없게
     * 만들지 않는다. 그룹이 사라지면 그 그룹 경유 공유는 자연히 판정에서 빠진다.
     */
    @Transactional
    public void delete(Long groupId) {
        Group group = requireOwner(groupId);
        group.setDeletedAt(Instant.now());
    }

    // -------------------------------------------------------------- 멤버십

    @Transactional
    public MemberResponse addMember(Long groupId, AddMemberRequest request) {
        Group group = requireOwner(groupId);

        User user = userRepository.findByEmail(request.email().trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "그 이메일로 가입한 사용자가 없습니다: " + request.email().trim()));

        if (memberRepository.findActive(groupId, user.getId()).isPresent()) {
            throw new IllegalArgumentException(user.getName() + " 님은 이미 이 그룹에 있습니다");
        }

        LocalDate joinedOn = request.joinedOn() != null ? request.joinedOn() : LocalDate.now();
        requireNotFuture(joinedOn, null);
        GroupMember member = memberRepository.save(GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupMember.Role.MEMBER)
                .joinedOn(joinedOn)
                .build());

        return MemberResponse.from(member, currentUser.id(),
                sharedUserIds(groupId, List.of(member)).contains(user.getId()));
    }

    /** 소속 기간을 고친다. 과거 프로젝트를 옮겨 적거나 날짜를 잘못 넣었을 때. */
    @Transactional
    public MemberResponse updateMember(Long groupId, Long memberId, UpdateMemberRequest request) {
        requireOwner(groupId);
        GroupMember member = requireMemberOf(groupId, memberId);

        if (request.leftOn() != null && request.leftOn().isBefore(request.joinedOn())) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다");
        }
        requireNotFuture(request.joinedOn(), request.leftOn());
        // 활성 멤버십은 하나뿐이다(부분 유니크 인덱스). DB 오류로 500이 되기 전에 막는다.
        if (request.leftOn() == null && member.getLeftOn() != null) {
            memberRepository.findActive(groupId, member.getUser().getId())
                    .filter(other -> !other.getId().equals(member.getId()))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException(
                                member.getUser().getName() + " 님은 이미 소속 중입니다");
                    });
        }

        member.setJoinedOn(request.joinedOn());
        member.setLeftOn(request.leftOn());

        return MemberResponse.from(member, currentUser.id(),
                sharedUserIds(groupId, List.of(member)).contains(member.getUser().getId()));
    }

    /**
     * 멤버를 내보낸다. 소유자만.
     *
     * <p><b>행을 지우지 않고 종료일을 적는다.</b> 그게 이 설계의 핵심이다.
     * 지우면 지난달 화면에서 그 사람이 사라져 그때의 인원 현황이 틀려진다.
     */
    @Transactional
    public void endMembership(Long groupId, Long memberId, LocalDate leftOn) {
        Group group = requireOwner(groupId);
        GroupMember member = requireMemberOf(groupId, memberId);

        if (member.getUser().getId().equals(group.getOwner().getId())) {
            throw new IllegalArgumentException("그룹 소유자는 내보낼 수 없습니다. 그룹을 지우세요");
        }
        if (member.getLeftOn() != null) {
            return;
        }
        requireNotFuture(null, leftOn);
        member.setLeftOn(effectiveLeftOn(member, leftOn));
    }

    /** 내가 나간다. 소유자를 제외한 누구나 스스로 할 수 있어야 한다. */
    @Transactional
    public void leave(Long groupId) {
        Long viewerId = currentUser.id();
        Group group = groupRepository.findActiveById(groupId)
                .orElseThrow(() -> new NotFoundException("그룹을 찾을 수 없습니다"));

        if (group.getOwner().getId().equals(viewerId)) {
            throw new IllegalArgumentException("그룹 소유자는 나갈 수 없습니다. 그룹을 지우세요");
        }
        GroupMember member = memberRepository.findActive(groupId, viewerId)
                .orElseThrow(() -> new NotFoundException("이 그룹에 소속돼 있지 않습니다"));

        member.setLeftOn(effectiveLeftOn(member, LocalDate.now()));
    }

    // ------------------------------------------------------------ 권한 판정

    /** 그룹을 볼 수 있는가. 소유자이거나 현재 소속 중이어야 한다. */
    @Transactional(readOnly = true)
    public Group requireVisible(Long groupId, Long viewerId) {
        Group group = groupRepository.findActiveById(groupId)
                .orElseThrow(() -> new NotFoundException("그룹을 찾을 수 없습니다"));

        if (isOwner(group, viewerId) || memberRepository.findActive(groupId, viewerId).isPresent()) {
            return group;
        }
        // 나간 사람에게 과거를 계속 보여주지 않는다. 소속이 끝나면 화면도 닫힌다.
        throw new AccessDeniedException("이 그룹을 볼 권한이 없습니다");
    }

    private Group requireOwner(Long groupId) {
        Group group = groupRepository.findActiveById(groupId)
                .orElseThrow(() -> new NotFoundException("그룹을 찾을 수 없습니다"));
        if (!isOwner(group, currentUser.id())) {
            throw new AccessDeniedException("그룹을 관리할 권한이 없습니다");
        }
        return group;
    }

    private GroupMember requireMemberOf(Long groupId, Long memberId) {
        GroupMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다"));
        // 다른 그룹의 멤버십 id를 넣어 남의 그룹을 고치지 못하게 한다.
        if (!member.getGroup().getId().equals(groupId)) {
            throw new NotFoundException("멤버를 찾을 수 없습니다");
        }
        return member;
    }

    // -------------------------------------------------------------- 보조

    private static boolean isOwner(Group group, Long userId) {
        return group.getOwner().getId().equals(userId);
    }

    /**
     * 멤버십 기간은 미래로 적을 수 없다.
     *
     * <p>스키마가 소속을 {@code left_on IS NULL}로 정의하고(활성 멤버십 부분 유니크
     * 인덱스), 권한 판정 · 인원수 · 공유 대상이 전부 그 정의를 쓴다. 종료일을
     * 2주 뒤로 적어두면 그 사람은 <b>오늘 당장</b> 그룹에서 안 보이게 되면서
     * 타임라인에는 2주 더 남아 있는, 설명할 수 없는 상태가 된다.
     *
     * <p>그래서 멤버십은 <b>예정이 아니라 기록</b>으로 못 박는다. 예정을 다루려면
     * "소속 중"의 정의를 여섯 군데에서 바꿔야 하고, 그건 이 기능이 감당할 값이 아니다.
     */
    private static void requireNotFuture(LocalDate joinedOn, LocalDate leftOn) {
        LocalDate today = LocalDate.now();
        if (joinedOn != null && joinedOn.isAfter(today)) {
            throw new IllegalArgumentException("시작일은 오늘보다 뒤일 수 없습니다");
        }
        if (leftOn != null && leftOn.isAfter(today)) {
            throw new IllegalArgumentException("종료일은 오늘보다 뒤일 수 없습니다. 나가는 날 적어 주세요");
        }
    }

    /** 종료일은 시작일보다 빠를 수 없다. CHECK 제약이 500으로 터지기 전에 맞춘다. */
    private static LocalDate effectiveLeftOn(GroupMember member, LocalDate requested) {
        LocalDate leftOn = requested != null ? requested : LocalDate.now();
        return leftOn.isBefore(member.getJoinedOn()) ? member.getJoinedOn() : leftOn;
    }

    private Map<Long, Long> countMembers(List<Long> groupIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : memberRepository.countCurrentMembers(groupIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** 이 그룹에 캘린더를 공유한 사람들. 멤버 목록에 "공유됨"을 표시하는 데 쓴다. */
    private Set<Long> sharedUserIds(Long groupId, List<GroupMember> members) {
        List<Long> userIds = members.stream().map(m -> m.getUser().getId()).distinct().toList();
        if (userIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> shared = new HashSet<>();
        for (CalendarShare share : shareRepository.findAcceptedForGroupByOwners(groupId, userIds)) {
            shared.add(share.getCalendar().getOwnerUser().getId());
        }
        return shared;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
