package io.github.codeonleo.leoshift.schedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 하루의 근무를 해석한다.
 *
 * <p>해석 순서는 아래와 같고, 뒤가 앞을 덮어쓴다.
 * <pre>
 *   근무 규칙(work_rules) → 휴가(leaves) → 날짜별 예외(day_overrides)
 * </pre>
 *
 * <p>메모는 코드와 별개로 해석된다. 예외에 코드 없이 메모만 있으면
 * 코드는 아래 계층에서 결정되고 메모만 얹힌다.
 *
 * <p><b>한 번 만들어서 여러 날을 해석하도록 설계했다.</b> 필요한 데이터를 생성자에서
 * 전부 받으므로 날짜마다 조회가 나가지 않는다. 이전 구현은 날짜당 두 번씩 질의해서
 * 한 달 화면에 80여 번의 조회가 발생했다.
 *
 * <p>불변이며 스레드 안전하다. 스프링에 의존하지 않는다.
 */
public final class ScheduleResolver {

    private final WorkRuleSet rules;
    /** startDate 오름차순 정렬. */
    private final List<LeavePeriod> leaves;
    private final Map<LocalDate, DayOverride> overrides;

    public ScheduleResolver(WorkRuleSet rules,
                            Collection<LeavePeriod> leaves,
                            Collection<DayOverride> overrides) {
        this.rules = rules == null ? WorkRuleSet.empty() : rules;

        List<LeavePeriod> sortedLeaves = leaves == null ? new ArrayList<>() : new ArrayList<>(leaves);
        sortedLeaves.sort(Comparator.comparing(LeavePeriod::startDate));
        this.leaves = List.copyOf(sortedLeaves);

        Map<LocalDate, DayOverride> byDate = new HashMap<>();
        if (overrides != null) {
            for (DayOverride override : overrides) {
                DayOverride previous = byDate.put(override.date(), override);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "같은 날짜에 예외가 둘 이상이다: " + override.date());
                }
            }
        }
        this.overrides = Map.copyOf(byDate);
    }

    /** 규칙만 있는 해석기. */
    public static ScheduleResolver ofRules(WorkRuleSet rules) {
        return new ScheduleResolver(rules, List.of(), List.of());
    }

    public ResolvedDay resolve(LocalDate date) {
        DayOverride override = overrides.get(date);
        String note = override == null ? null : override.note();

        if (override != null && override.code() != null) {
            return new ResolvedDay(date, override.code(), ResolvedDay.Source.OVERRIDE, override.id(), note);
        }

        LeavePeriod leave = findLeave(date);
        if (leave != null) {
            return new ResolvedDay(date, leave.code(), ResolvedDay.Source.LEAVE, leave.id(), note);
        }

        Optional<WorkRule> rule = rules.ruleFor(date);
        if (rule.isPresent()) {
            WorkRule matched = rule.get();
            return new ResolvedDay(date, matched.codeAt(date), ResolvedDay.Source.RULE, matched.id(), note);
        }

        return new ResolvedDay(date, null, ResolvedDay.Source.NONE, null, note);
    }

    /**
     * 기간을 해석한다. 양 끝을 포함한다.
     *
     * @return {@code to}가 {@code from}보다 빠르면 빈 목록
     */
    public List<ResolvedDay> resolveRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        List<ResolvedDay> days = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            days.add(resolve(cursor));
        }
        return List.copyOf(days);
    }

    /**
     * 해당 날짜를 덮는 휴가.
     *
     * <p>DB가 휴가 기간의 겹침을 막지 않으므로 둘 이상이 걸릴 수 있다.
     * 이때는 <b>나중에 시작한 것</b>이 이긴다. 같은 날 시작이면 id가 큰 쪽이다.
     * 어느 쪽이든 항상 같은 답이 나오는 것이 중요하다.
     */
    private LeavePeriod findLeave(LocalDate date) {
        LeavePeriod best = null;
        for (LeavePeriod leave : leaves) {
            if (leave.startDate().isAfter(date)) {
                break; // startDate 오름차순이라 이후는 볼 필요 없다
            }
            if (!leave.covers(date)) {
                continue;
            }
            if (best == null || isPreferred(leave, best)) {
                best = leave;
            }
        }
        return best;
    }

    private static boolean isPreferred(LeavePeriod candidate, LeavePeriod current) {
        int byStart = candidate.startDate().compareTo(current.startDate());
        if (byStart != 0) {
            return byStart > 0;
        }
        long candidateId = candidate.id() == null ? Long.MIN_VALUE : candidate.id();
        long currentId = current.id() == null ? Long.MIN_VALUE : current.id();
        return candidateId > currentId;
    }
}
