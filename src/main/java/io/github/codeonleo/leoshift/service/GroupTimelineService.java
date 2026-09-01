package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.calendar.CalendarShare;
import io.github.codeonleo.leoshift.domain.group.Group;
import io.github.codeonleo.leoshift.domain.group.GroupMember;
import io.github.codeonleo.leoshift.domain.user.User;
import io.github.codeonleo.leoshift.domain.work.DayOverride;
import io.github.codeonleo.leoshift.domain.work.Leave;
import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.repository.CalendarRepository;
import io.github.codeonleo.leoshift.repository.CalendarShareRepository;
import io.github.codeonleo.leoshift.repository.DayOverrideRepository;
import io.github.codeonleo.leoshift.repository.GroupMemberRepository;
import io.github.codeonleo.leoshift.repository.LeaveRepository;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.repository.WorkRuleRepository;
import io.github.codeonleo.leoshift.schedule.ResolvedDay;
import io.github.codeonleo.leoshift.schedule.ScheduleResolver;
import io.github.codeonleo.leoshift.schedule.WorkRuleSet;
import io.github.codeonleo.leoshift.security.CurrentUser;
import io.github.codeonleo.leoshift.web.dto.TimelineDtos.TimelineDayResponse;
import io.github.codeonleo.leoshift.web.dto.TimelineDtos.TimelineResponse;
import io.github.codeonleo.leoshift.web.dto.TimelineDtos.TimelineRowResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 타임라인. 이 앱의 핵심 차별점이다.
 *
 * <p>멤버 × 날짜 격자와 날짜별 인원. 데이터는 전부 각자의 개인 캘린더에서 온다.
 * 그룹은 아무것도 저장하지 않으므로, 사람이 프로젝트를 옮겨도 그 사람의 근무와
 * 휴가는 그대로 따라간다.
 *
 * <p>조회는 <b>사람 수와 무관하게 고정 횟수</b>다. 근무 규칙 · 휴가 · 날짜별 변경 ·
 * 근무 코드를 각각 한 번씩, 관련된 캘린더를 통째로 가져와 캘린더별 해석기를 만든다.
 * 사람마다 조회하면 열 명짜리 프로젝트의 한 달 화면에 수십 번의 질의가 나간다.
 */
@Service
public class GroupTimelineService {

    /** 한 번에 그릴 수 있는 최대 기간. 월 격자(최대 6주)를 넉넉히 담는다. */
    private static final long MAX_RANGE_DAYS = 100;

    private final GroupService groupService;
    private final GroupMemberRepository memberRepository;
    private final CalendarShareRepository shareRepository;
    private final CalendarRepository calendarRepository;
    private final ScheduleTypeRepository scheduleTypeRepository;
    private final WorkRuleRepository workRuleRepository;
    private final LeaveRepository leaveRepository;
    private final DayOverrideRepository dayOverrideRepository;
    private final CurrentUser currentUser;

    public GroupTimelineService(GroupService groupService,
                                GroupMemberRepository memberRepository,
                                CalendarShareRepository shareRepository,
                                CalendarRepository calendarRepository,
                                ScheduleTypeRepository scheduleTypeRepository,
                                WorkRuleRepository workRuleRepository,
                                LeaveRepository leaveRepository,
                                DayOverrideRepository dayOverrideRepository,
                                CurrentUser currentUser) {
        this.groupService = groupService;
        this.memberRepository = memberRepository;
        this.shareRepository = shareRepository;
        this.calendarRepository = calendarRepository;
        this.scheduleTypeRepository = scheduleTypeRepository;
        this.workRuleRepository = workRuleRepository;
        this.leaveRepository = leaveRepository;
        this.dayOverrideRepository = dayOverrideRepository;
        this.currentUser = currentUser;
    }

    /** 한 사람이 이 그룹에서 쓰는 캘린더와, 그 공유가 어디까지 열려 있는지. */
    private record MemberCalendar(Calendar calendar, CalendarShare.Visibility visibility) {
    }

    @Transactional(readOnly = true)
    public TimelineResponse timeline(Long groupId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("한 번에 조회할 수 있는 기간을 넘었습니다");
        }

        Long viewerId = currentUser.id();
        Group group = groupService.requireVisible(groupId, viewerId);

        // 기간에 걸치는 멤버십. 한 사람이 나갔다 다시 들어왔으면 행이 여러 개다.
        Map<Long, List<GroupMember>> byUser = new LinkedHashMap<>();
        for (GroupMember member : memberRepository.findOverlapping(groupId, from, to)) {
            byUser.computeIfAbsent(member.getUser().getId(), id -> new ArrayList<>()).add(member);
        }
        List<LocalDate> dates = datesBetween(from, to);
        if (byUser.isEmpty()) {
            return empty(group, from, to, dates);
        }

        Map<Long, MemberCalendar> calendars = sharedCalendars(groupId, List.copyOf(byUser.keySet()));
        boolean viewerShared = calendars.containsKey(viewerId);

        // 내 줄은 공유하지 않았어도 보인다. 남에게 어떻게 보일지와 내가 무엇을
        // 공유하지 않았는지를 같은 화면에서 알 수 있어야 한다.
        if (byUser.containsKey(viewerId) && !viewerShared) {
            calendarRepository.findWorkCalendarsOf(viewerId).stream().findFirst().ifPresent(
                    calendar -> calendars.put(viewerId,
                            new MemberCalendar(calendar, CalendarShare.Visibility.FULL)));
        }

        List<Long> calendarIds = calendars.values().stream()
                .map(entry -> entry.calendar().getId())
                .toList();
        Map<Long, ScheduleResolver> resolvers = resolvers(calendarIds, from, to);
        Map<Long, Map<String, ScheduleType>> types = scheduleTypes(calendarIds);

        List<TimelineRowResponse> rows = byUser.entrySet().stream()
                .map(entry -> row(entry.getKey(), entry.getValue(), calendars.get(entry.getKey()),
                        resolvers, types, dates, viewerId))
                .sorted(Comparator
                        .comparing(TimelineRowResponse::self).reversed()
                        .thenComparing(TimelineRowResponse::displayName))
                .toList();

        return new TimelineResponse(
                group.getId(), group.getName(), group.getKind().name(),
                from, to, dates, rows,
                count(rows, dates, ScheduleType.Category.WORK),
                count(rows, dates, ScheduleType.Category.LEAVE),
                viewerShared);
    }

    // ---------------------------------------------------------------- 한 줄

    private TimelineRowResponse row(Long userId,
                                    List<GroupMember> memberships,
                                    MemberCalendar shared,
                                    Map<Long, ScheduleResolver> resolvers,
                                    Map<Long, Map<String, ScheduleType>> types,
                                    List<LocalDate> dates,
                                    Long viewerId) {

        User user = memberships.get(0).getUser();
        boolean self = userId.equals(viewerId);

        if (shared == null) {
            // 그룹에는 있지만 캘린더를 공유하지 않은 사람. 줄은 비어 있어도 보여야
            // "저 사람이 아직 공유를 안 했구나"를 알 수 있다.
            List<TimelineDayResponse> days = dates.stream()
                    .map(date -> activeOn(memberships, date)
                            ? TimelineDayResponse.blank(date)
                            : TimelineDayResponse.outside(date))
                    .toList();
            return new TimelineRowResponse(userId, displayName(user), user.getColorTag(), self, false, days);
        }

        Long calendarId = shared.calendar().getId();
        ScheduleResolver resolver = resolvers.get(calendarId);
        Map<String, ScheduleType> codes = types.getOrDefault(calendarId, Map.of());
        boolean busyOnly = shared.visibility() == CalendarShare.Visibility.BUSY_ONLY;

        List<ResolvedDay> resolved = resolver.resolveRange(dates.get(0), dates.get(dates.size() - 1));
        Map<LocalDate, ResolvedDay> byDate = resolved.stream()
                .collect(Collectors.toMap(ResolvedDay::date, day -> day, (a, b) -> a));

        List<TimelineDayResponse> days = dates.stream()
                .map(date -> {
                    if (!activeOn(memberships, date)) {
                        return TimelineDayResponse.outside(date);
                    }
                    ResolvedDay day = byDate.get(date);
                    if (day == null || day.code() == null) {
                        return TimelineDayResponse.blank(date);
                    }
                    ScheduleType type = codes.get(day.code());
                    String category = type != null ? type.getCategory().name() : null;

                    // 바쁨만 공유면 무엇으로 바쁜지는 내보내지 않는다. 있고 없고만 알린다.
                    if (busyOnly) {
                        return new TimelineDayResponse(date, null, null, null, category, true);
                    }
                    return new TimelineDayResponse(
                            date, day.code(),
                            type != null ? type.getName() : day.code(),
                            type != null ? type.getColor() : null,
                            category, true);
                })
                .toList();

        return new TimelineRowResponse(userId, displayName(user), user.getColorTag(), self, true, days);
    }

    // ---------------------------------------------------------------- 조회

    /**
     * 이 그룹에 공유된 멤버들의 근무 캘린더.
     *
     * <p>근무 캘린더만 본다. 그룹 타임라인이 답하는 질문은 "그날 누가 있는가"이고,
     * 개인 일정 캘린더는 여기에 나올 자리가 아니다. 한 사람이 근무 캘린더를 여러 개
     * 공유했다면 가장 먼저 만든 것을 쓴다.
     */
    private Map<Long, MemberCalendar> sharedCalendars(Long groupId, List<Long> userIds) {
        Map<Long, MemberCalendar> byUser = new HashMap<>();

        List<CalendarShare> shares = new ArrayList<>(
                shareRepository.findAcceptedForGroupByOwners(groupId, userIds));
        shares.sort(Comparator.comparing(share -> share.getCalendar().getId()));

        for (CalendarShare share : shares) {
            Calendar calendar = share.getCalendar();
            if (calendar.getKind() != Calendar.Kind.WORK || calendar.getOwnerUser() == null) {
                continue;
            }
            byUser.putIfAbsent(calendar.getOwnerUser().getId(),
                    new MemberCalendar(calendar, share.getVisibility()));
        }
        return byUser;
    }

    /** 캘린더별 해석기. 세 번의 조회로 전부 가져와 나눈다. */
    private Map<Long, ScheduleResolver> resolvers(List<Long> calendarIds, LocalDate from, LocalDate to) {
        if (calendarIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<WorkRule>> rules = groupBy(
                workRuleRepository.findOverlappingForCalendars(calendarIds, from, to),
                rule -> rule.getCalendar().getId());
        Map<Long, List<Leave>> leaves = groupBy(
                leaveRepository.findOverlappingForCalendars(calendarIds, from, to),
                leave -> leave.getCalendar().getId());
        Map<Long, List<DayOverride>> overrides = groupBy(
                dayOverrideRepository.findInRangeForCalendars(calendarIds, from, to),
                override -> override.getCalendar().getId());

        Map<Long, ScheduleResolver> resolvers = new HashMap<>();
        for (Long calendarId : calendarIds) {
            resolvers.put(calendarId, new ScheduleResolver(
                    WorkRuleSet.of(rules.getOrDefault(calendarId, List.of()).stream()
                            .map(WorkRule::toDomain).toList()),
                    leaves.getOrDefault(calendarId, List.of()).stream()
                            .map(Leave::toDomain).toList(),
                    overrides.getOrDefault(calendarId, List.of()).stream()
                            .map(DayOverride::toDomain).toList()));
        }
        return resolvers;
    }

    /**
     * 캘린더별 코드 사전.
     *
     * <p>사람마다 코드가 다르므로("N" vs "야간") 색과 이름은 각자의 캘린더에서 온다.
     * 격자를 가로질러 집계할 수 있는 건 시스템 값인 {@code category}뿐이고,
     * 스키마에서 category만 사용자가 못 정하게 한 이유가 이것이다.
     */
    private Map<Long, Map<String, ScheduleType>> scheduleTypes(List<Long> calendarIds) {
        if (calendarIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, ScheduleType>> byCalendar = new HashMap<>();
        for (ScheduleType type : scheduleTypeRepository.findByCalendars(calendarIds)) {
            byCalendar.computeIfAbsent(type.getCalendar().getId(), id -> new HashMap<>())
                    .put(type.getCode(), type);
        }
        return byCalendar;
    }

    // ---------------------------------------------------------------- 보조

    private static <T> Map<Long, List<T>> groupBy(List<T> items, java.util.function.Function<T, Long> key) {
        Map<Long, List<T>> grouped = new HashMap<>();
        for (T item : items) {
            grouped.computeIfAbsent(key.apply(item), id -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private static boolean activeOn(List<GroupMember> memberships, LocalDate date) {
        return memberships.stream().anyMatch(member -> member.activeOn(date));
    }

    private static String displayName(User user) {
        return user.getNickname() != null && !user.getNickname().isBlank()
                ? user.getNickname()
                : user.getName();
    }

    private static List<LocalDate> datesBetween(LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            dates.add(cursor);
        }
        return dates;
    }

    /** 날짜별 인원. 코드가 아니라 category로 세므로 사람마다 코드가 달라도 합쳐진다. */
    private static List<Integer> count(List<TimelineRowResponse> rows, List<LocalDate> dates,
                                       ScheduleType.Category category) {
        List<Integer> counts = new ArrayList<>(dates.size());
        for (int i = 0; i < dates.size(); i++) {
            int total = 0;
            for (TimelineRowResponse row : rows) {
                TimelineDayResponse day = row.days().get(i);
                if (day.member() && category.name().equals(day.category())) {
                    total++;
                }
            }
            counts.add(total);
        }
        return counts;
    }

    private static TimelineResponse empty(Group group, LocalDate from, LocalDate to, List<LocalDate> dates) {
        List<Integer> zeros = dates.stream().map(date -> 0).toList();
        return new TimelineResponse(
                group.getId(), group.getName(), group.getKind().name(),
                from, to, dates, List.of(), zeros, zeros, false);
    }
}
