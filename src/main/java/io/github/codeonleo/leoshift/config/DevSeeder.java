package io.github.codeonleo.leoshift.config;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.calendar.CalendarShare;
import io.github.codeonleo.leoshift.domain.group.Group;
import io.github.codeonleo.leoshift.domain.group.GroupMember;
import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.domain.work.Leave;
import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.CalendarShareRepository;
import io.github.codeonleo.leoshift.repository.GroupMemberRepository;
import io.github.codeonleo.leoshift.repository.GroupRepository;
import io.github.codeonleo.leoshift.repository.LeaveRepository;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.repository.UserRepository;
import io.github.codeonleo.leoshift.repository.WorkRuleRepository;
import io.github.codeonleo.leoshift.schedule.preset.PatternPreset;
import io.github.codeonleo.leoshift.schedule.preset.PatternPresets;
import io.github.codeonleo.leoshift.schedule.preset.ScheduleTypeSpec;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 개발용 시드.
 *
 * <p>혼자 쓰는 화면은 사용자 하나로 확인되지만 <b>그룹 타임라인은 그렇지 않다.</b>
 * 한 줄짜리 격자로는 조가 엇갈리는지, 나간 사람이 그 달에 남는지, 공유하지 않은
 * 사람의 줄이 어떻게 보이는지를 전혀 확인할 수 없다. 그래서 프로젝트 하나를
 * 통째로 만든다.
 *
 * <p>일부러 어긋나게 심어둔 것들이 있다.
 * <ul>
 *   <li>박하윤은 캘린더를 공유하지 않았다 — "아직 공유 안 함" 줄이 보여야 한다
 *   <li>정우성은 지난달에 프로젝트를 떠났다 — 이번 달 화면에서는 회색이어야 한다
 *   <li>김수진은 이번 달에 휴가가 있다 — 부재 인원 집계가 0이 아니어야 한다
 * </ul>
 *
 * <p>조각마다 있는지 확인하고 없을 때만 만든다. 개발 중에 시드를 늘려도
 * DB를 지우지 않고 다시 띄우면 새 조각만 채워진다.
 */
@Component
@Profile("local")
class DevSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    private static final String SHIFT_PRESET = "kr.shift.4team3shift";
    private static final String REGULAR_PRESET = "kr.regular.weekday5";
    private static final String GROUP_NAME = "OO프로젝트";

    /** 한 사람. 조가 다르면 같은 프리셋이어도 격자가 엇갈려 보인다. */
    private record Person(String email, String name, String nickname, String color,
                          String presetId, String team, boolean shares) {
    }

    private static final List<Person> TEAMMATES = List.of(
            new Person("sujin@localhost", "김수진", "수진", "#DB2777", SHIFT_PRESET, "1조", true),
            new Person("minho@localhost", "이민호", "민호", "#059669", SHIFT_PRESET, "3조", true),
            new Person("hayoon@localhost", "박하윤", "하윤", "#D97706", REGULAR_PRESET, null, false),
            new Person("woosung@localhost", "정우성", "우성", "#7C3AED", SHIFT_PRESET, "4조", true));

    private final UserRepository userRepository;
    private final CalendarRepository calendarRepository;
    private final ScheduleTypeRepository scheduleTypeRepository;
    private final WorkRuleRepository workRuleRepository;
    private final LeaveRepository leaveRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final CalendarShareRepository shareRepository;

    DevSeeder(UserRepository userRepository,
              CalendarRepository calendarRepository,
              ScheduleTypeRepository scheduleTypeRepository,
              WorkRuleRepository workRuleRepository,
              LeaveRepository leaveRepository,
              GroupRepository groupRepository,
              GroupMemberRepository memberRepository,
              CalendarShareRepository shareRepository) {
        this.userRepository = userRepository;
        this.calendarRepository = calendarRepository;
        this.scheduleTypeRepository = scheduleTypeRepository;
        this.workRuleRepository = workRuleRepository;
        this.leaveRepository = leaveRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.shareRepository = shareRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        PatternPresets presets = PatternPresets.load();
        LocalDate firstOfMonth = LocalDate.now().withDayOfMonth(1);

        User dev = ensureUser(LocalDevConfig.DEV_EMAIL, "개발자", "dev", "#2563EB");
        Calendar devCalendar = ensureWorkCalendar(dev, presets, SHIFT_PRESET, "2조", firstOfMonth);

        Group group = ensureGroup(dev);
        ensureMembership(group, dev, firstOfMonth.minusMonths(6), null, GroupMember.Role.OWNER);
        ensureShare(devCalendar, group, dev);

        for (Person person : TEAMMATES) {
            User user = ensureUser(person.email(), person.name(), person.nickname(), person.color());
            Calendar calendar = ensureWorkCalendar(
                    user, presets, person.presetId(), person.team(), firstOfMonth);

            // 정우성은 지난달에 나갔다. 행을 지우지 않고 종료일을 적는 것이 이 설계의 핵심이라,
            // 지난달 화면에는 남고 이번 달 화면에서는 소속 밖으로 표시돼야 한다.
            LocalDate leftOn = person.email().startsWith("woosung")
                    ? firstOfMonth.minusDays(1)
                    : null;
            ensureMembership(group, user, firstOfMonth.minusMonths(3), leftOn, GroupMember.Role.MEMBER);

            if (person.shares()) {
                ensureShare(calendar, group, user);
            }
            if (person.email().startsWith("sujin")) {
                ensureLeave(calendar, user, firstOfMonth.plusDays(9), firstOfMonth.plusDays(11));
            }
        }

        log.info("개발용 시드 확인 완료: {} · 그룹 {}", LocalDevConfig.DEV_EMAIL, GROUP_NAME);
    }

    // ---------------------------------------------------------------- 조각

    private User ensureUser(String email, String name, String nickname, String color) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
                .email(email)
                .name(name)
                .nickname(nickname)
                .colorTag(color)
                .build()));
    }

    /** 근무 캘린더 + 프리셋에서 온 근무 코드 + 반복 규칙. */
    private Calendar ensureWorkCalendar(User user, PatternPresets presets,
                                        String presetId, String team, LocalDate anchor) {
        List<Calendar> existing = calendarRepository.findWorkCalendarsOf(user.getId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        Calendar calendar = calendarRepository.save(Calendar.builder()
                .ownerUser(user)
                .name("내 근무")
                .kind(Calendar.Kind.WORK)
                .color(user.getColorTag())
                .isDefault(true)
                .build());

        PatternPreset preset = presets.require(presetId);

        int order = 10;
        for (ScheduleTypeSpec spec : presets.scheduleTypesFor(preset)) {
            scheduleTypeRepository.save(ScheduleType.builder()
                    .calendar(calendar)
                    .code(spec.code())
                    .name(spec.name())
                    .color(spec.color())
                    .category(ScheduleType.Category.valueOf(spec.category().name()))
                    .startTime(spec.startTime())
                    .endTime(spec.endTime())
                    .crossesMidnight(spec.crossesMidnight())
                    .halfDay(spec.halfDay())
                    .sortOrder(order)
                    .build());
            order += 10;
        }

        List<String> sequence = team != null ? preset.sequenceFor(team) : preset.sequence();
        LocalDate anchorDate = preset.snapAnchor(anchor);

        workRuleRepository.save(WorkRule.builder()
                .calendar(calendar)
                .anchorDate(anchorDate)
                .cycleLength(sequence.size())
                .codeSequence(sequence)
                .effectiveFrom(anchorDate.minusYears(1))
                .sourcePresetId(preset.id())
                .build());

        return calendar;
    }

    private Group ensureGroup(User owner) {
        return groupRepository.findOwnedBy(owner.getId()).stream()
                .filter(group -> GROUP_NAME.equals(group.getName()))
                .findFirst()
                .orElseGet(() -> groupRepository.save(Group.builder()
                        .owner(owner)
                        .name(GROUP_NAME)
                        .kind(Group.Kind.PROJECT)
                        .description("교대 인원의 부재 현황을 보려고 만든 프로젝트")
                        .color("#2563EB")
                        .build()));
    }

    private void ensureMembership(Group group, User user, LocalDate joinedOn,
                                  LocalDate leftOn, GroupMember.Role role) {
        boolean known = memberRepository.findAllOf(group.getId()).stream()
                .anyMatch(member -> member.getUser().getId().equals(user.getId()));
        if (known) {
            return;
        }
        memberRepository.save(GroupMember.builder()
                .group(group)
                .user(user)
                .role(role)
                .joinedOn(joinedOn)
                .leftOn(leftOn)
                .build());
    }

    private void ensureShare(Calendar calendar, Group group, User owner) {
        if (shareRepository.findLiveGroupShare(calendar.getId(), group.getId()).isPresent()) {
            return;
        }
        shareRepository.save(CalendarShare.builder()
                .calendar(calendar)
                .granteeGroup(group)
                .permission(CalendarShare.Permission.VIEW)
                .visibility(CalendarShare.Visibility.FULL)
                .status(CalendarShare.Status.ACCEPTED)
                .createdBy(owner)
                .build());
    }

    private void ensureLeave(Calendar calendar, User user, LocalDate from, LocalDate to) {
        if (!leaveRepository.findOverlapping(calendar.getId(), from, to).isEmpty()) {
            return;
        }
        boolean hasAnnual = scheduleTypeRepository
                .findByCalendarAndCode(calendar.getId(), "ANNUAL").isPresent();
        if (!hasAnnual) {
            return;
        }
        leaveRepository.save(Leave.builder()
                .calendar(calendar)
                .startDate(from)
                .endDate(to)
                .scheduleTypeCode("ANNUAL")
                .note("가족 여행")
                .createdBy(user)
                .build());
    }
}
