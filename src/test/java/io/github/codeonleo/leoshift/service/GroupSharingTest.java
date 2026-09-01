package io.github.codeonleo.leoshift.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.codeonleo.leoshift.AbstractPostgresTest;
import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.group.Group;
import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.repository.WorkRuleRepository;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.AddMemberRequest;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.GroupSummaryResponse;
import io.github.codeonleo.leoshift.web.dto.GroupDtos.SaveGroupRequest;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.SetShareRequest;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.ShareLevel;
import io.github.codeonleo.leoshift.web.dto.ShareDtos.TargetType;
import io.github.codeonleo.leoshift.web.dto.TimelineDtos.TimelineResponse;
import io.github.codeonleo.leoshift.web.dto.TimelineDtos.TimelineRowResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 그룹과 공유의 규칙을 못 박는다.
 *
 * <p>여기 담긴 것들은 <b>화면을 보면서는 틀린 줄 모르는</b> 규칙들이다.
 * 특히 "그룹에 넣는 것과 일정이 보이는 것은 별개"와 "떠난 사람은 지금은 안 보이되
 * 있던 기간에는 남는다"는, 조금만 어긋나도 남의 일정이 새거나 프로젝트 기록이
 * 사라지는 쪽으로 조용히 무너진다.
 */
@SpringBootTest
@DisplayName("그룹 · 공유")
class GroupSharingTest extends AbstractPostgresTest {

    @Autowired private UserRepository userRepository;
    @Autowired private CalendarRepository calendarRepository;
    @Autowired private ScheduleTypeRepository scheduleTypeRepository;
    @Autowired private WorkRuleRepository workRuleRepository;
    @Autowired private GroupService groupService;
    @Autowired private SharingService sharingService;
    @Autowired private GroupTimelineService timelineService;
    @Autowired private CalendarAccessService accessService;

    private static final LocalDate ANCHOR = LocalDate.of(2026, 3, 2);
    private static final LocalDate FROM = LocalDate.of(2026, 3, 2);
    private static final LocalDate TO = LocalDate.of(2026, 3, 8);

    private User owner;
    private User mate;

    @BeforeEach
    void setUp() {
        owner = newUserWithWorkCalendar("owner");
        mate = newUserWithWorkCalendar("mate");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("그룹에 넣는 것만으로는 그 사람의 일정이 보이지 않는다")
    void membershipAloneRevealsNothing() {
        loginAs(owner);
        Long groupId = createGroupWith(mate);

        TimelineRowResponse row = rowOf(timelineService.timeline(groupId, FROM, TO), mate);

        assertThat(row.shared()).isFalse();
        assertThat(row.days()).allSatisfy(day -> {
            assertThat(day.member()).isTrue();
            assertThat(day.code()).isNull();
        });
    }

    @Test
    @DisplayName("공유하면 그때부터 타임라인에 근무가 나온다")
    void sharingRevealsSchedule() {
        loginAs(owner);
        Long groupId = createGroupWith(mate);

        loginAs(mate);
        sharingService.setLevel(new SetShareRequest(TargetType.GROUP, groupId, null, ShareLevel.WORK_ONLY));

        loginAs(owner);
        TimelineRowResponse row = rowOf(timelineService.timeline(groupId, FROM, TO), mate);

        assertThat(row.shared()).isTrue();
        assertThat(row.days()).extracting(day -> day.code()).containsExactly(
                "D", "D", "D", "O", "O", "D", "D");
        assertThat(row.days().get(0).category()).isEqualTo("WORK");
    }

    @Test
    @DisplayName("그룹을 떠나면 지금은 안 보이지만, 있던 기간의 기록은 남는다")
    void leavingHidesNowButKeepsHistory() {
        loginAs(owner);
        Long groupId = createGroupWith(mate);

        loginAs(mate);
        sharingService.setLevel(new SetShareRequest(TargetType.GROUP, groupId, null, ShareLevel.WORK_ONLY));
        // 오늘까지만 소속. 멤버십은 기록이므로 미래 날짜로는 적을 수 없다.
        groupService.leave(groupId);

        loginAs(owner);

        // 날짜 개념이 없는 권한 판정은 "현재" 소속을 본다.
        assertThat(accessService.listVisible())
                .extracting(access -> access.calendar().getOwnerUser().getId())
                .doesNotContain(mate.getId());

        // 기간을 아는 타임라인은 "그때" 소속을 본다. 지난달 프로젝트 기록이 사라지면 안 된다.
        LocalDate joined = LocalDate.now().minusDays(3);
        TimelineRowResponse row = rowOf(
                timelineService.timeline(groupId, joined, LocalDate.now()), mate);
        assertThat(row.shared()).isTrue();
        assertThat(row.days()).anySatisfy(day -> assertThat(day.code()).isNotNull());
    }

    @Test
    @DisplayName("개인 공유는 상대가 수락해야 유효하다")
    void directShareNeedsAcceptance() {
        loginAs(owner);
        sharingService.setLevel(
                new SetShareRequest(TargetType.USER, null, mate.getEmail(), ShareLevel.WORK_ONLY));

        loginAs(mate);
        assertThat(visibleOwnerIds()).doesNotContain(owner.getId());

        Long shareId = sharingService.overview().incoming().get(0).id();
        sharingService.respond(shareId, true);

        assertThat(visibleOwnerIds()).contains(owner.getId());
    }

    @Test
    @DisplayName("단계를 전체에서 근무만으로 낮추면 개인 캘린더 공유가 끊긴다")
    void loweringLevelRevokesPersonalCalendar() {
        Calendar personal = calendarRepository.save(Calendar.builder()
                .ownerUser(owner)
                .name("개인 일정")
                .kind(Calendar.Kind.GENERAL)
                .build());

        loginAs(owner);
        Long groupId = createGroupWith(mate);
        sharingService.setLevel(new SetShareRequest(TargetType.GROUP, groupId, null, ShareLevel.FULL));

        loginAs(mate);
        assertThat(visibleCalendarIds()).contains(personal.getId());

        loginAs(owner);
        sharingService.setLevel(new SetShareRequest(TargetType.GROUP, groupId, null, ShareLevel.WORK_ONLY));

        // 권한이 더해지기만 하면 안 된다. 이전 구현은 낮추기가 아예 불가능했다.
        loginAs(mate);
        assertThat(visibleCalendarIds()).doesNotContain(personal.getId());
    }

    @Test
    @DisplayName("소속되지 않은 그룹에는 공유할 수 없다")
    void cannotShareToForeignGroup() {
        loginAs(owner);
        Long groupId = createGroupWith(null);

        loginAs(mate);
        assertThatThrownBy(() ->
                sharingService.setLevel(
                        new SetShareRequest(TargetType.GROUP, groupId, null, ShareLevel.WORK_ONLY)))
                .isInstanceOf(CalendarAccessService.AccessDeniedException.class);
    }

    @Test
    @DisplayName("그룹을 떠난 사람은 그 그룹 화면을 열 수 없다")
    void formerMemberCannotOpenGroup() {
        loginAs(owner);
        Long groupId = createGroupWith(mate);

        loginAs(mate);
        groupService.leave(groupId);

        assertThatThrownBy(() -> timelineService.timeline(groupId, FROM, TO))
                .isInstanceOf(CalendarAccessService.AccessDeniedException.class);
    }

    // ---------------------------------------------------------------- 보조

    private void loginAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getId(), null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    /** 로그인한 사람이 소유자이고, mate가 주어지면 멤버로 넣은 그룹. */
    private Long createGroupWith(User member) {
        GroupSummaryResponse group = groupService.create(
                new SaveGroupRequest("프로젝트-" + UUID.randomUUID(), Group.Kind.PROJECT, null, null));
        if (member != null) {
            // 검증 구간(3월)보다 앞서 합류시킨다. 기본값인 오늘로 넣으면 그 구간에
            // 소속이 아니라서 줄 자체가 나오지 않는다 — 멤버십에 기간이 있다는 뜻이다.
            groupService.addMember(group.id(),
                    new AddMemberRequest(member.getEmail(), FROM.minusMonths(1)));
        }
        return group.id();
    }

    private static TimelineRowResponse rowOf(TimelineResponse timeline, User user) {
        return timeline.rows().stream()
                .filter(row -> row.userId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(user.getName() + " 줄이 없다"));
    }

    private List<Long> visibleOwnerIds() {
        return accessService.listVisible().stream()
                .map(access -> access.calendar().getOwnerUser().getId())
                .toList();
    }

    private List<Long> visibleCalendarIds() {
        return accessService.listVisible().stream()
                .map(access -> access.calendar().getId())
                .toList();
    }

    /** 월~수 주간, 목금 휴무, 토일 주간인 단순한 주기. 요일과 어긋나지 않게 월요일을 기준일로 둔다. */
    private User newUserWithWorkCalendar(String prefix) {
        User user = userRepository.save(User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@test.local")
                .name(prefix)
                .build());

        Calendar calendar = calendarRepository.save(Calendar.builder()
                .ownerUser(user)
                .name("내 근무")
                .kind(Calendar.Kind.WORK)
                .isDefault(true)
                .build());

        // 근무 코드에는 시각이 있어야 한다(schedule_types_worktime_chk).
        scheduleTypeRepository.save(ScheduleType.builder()
                .calendar(calendar).code("D").name("주간").color("#2563EB")
                .category(ScheduleType.Category.WORK)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(18, 0))
                .sortOrder(10).build());
        scheduleTypeRepository.save(ScheduleType.builder()
                .calendar(calendar).code("O").name("휴무").color("#94A3B8")
                .category(ScheduleType.Category.OFF).sortOrder(20).build());

        List<String> sequence = List.of("D", "D", "D", "O", "O", "D", "D");
        workRuleRepository.save(WorkRule.builder()
                .calendar(calendar)
                .anchorDate(ANCHOR)
                .cycleLength(sequence.size())
                .codeSequence(sequence)
                .effectiveFrom(ANCHOR.minusYears(1))
                .sourcePresetId(null)
                .build());

        return user;
    }
}
