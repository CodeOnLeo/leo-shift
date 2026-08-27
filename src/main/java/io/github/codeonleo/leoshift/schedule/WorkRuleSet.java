package io.github.codeonleo.leoshift.schedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 한 캘린더의 근무 규칙 모음.
 *
 * <p>유효기간이 겹치는 규칙을 거부한다. 겹침이 없으면 어떤 날짜에도 적용될 규칙이
 * 최대 하나로 정해지므로, 화면마다 다른 근무가 나오는 일이 구조적으로 불가능해진다.
 * (DB의 {@code work_rules_no_overlap} 배제 제약과 같은 불변식을 코드에서도 지킨다.)
 */
public final class WorkRuleSet {

    private static final WorkRuleSet EMPTY = new WorkRuleSet(List.of());

    /** effectiveFrom 오름차순으로 정렬된 규칙들. 겹치지 않음이 보장된다. */
    private final List<WorkRule> rules;

    private WorkRuleSet(List<WorkRule> rules) {
        this.rules = rules;
    }

    public static WorkRuleSet empty() {
        return EMPTY;
    }

    public static WorkRuleSet of(WorkRule... rules) {
        return of(List.of(rules));
    }

    /**
     * @throws IllegalArgumentException 유효기간이 겹치는 규칙이 있으면
     */
    public static WorkRuleSet of(Collection<WorkRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return EMPTY;
        }
        List<WorkRule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparing(WorkRule::effectiveFrom));

        for (int i = 1; i < sorted.size(); i++) {
            WorkRule previous = sorted.get(i - 1);
            WorkRule current = sorted.get(i);
            if (previous.effectiveTo() == null || !previous.effectiveTo().isBefore(current.effectiveFrom())) {
                throw new IllegalArgumentException(
                        "근무 규칙의 유효기간이 겹친다: "
                                + describe(previous) + " 와 " + describe(current));
            }
        }
        return new WorkRuleSet(List.copyOf(sorted));
    }

    private static String describe(WorkRule rule) {
        return "[" + rule.effectiveFrom() + " ~ "
                + (rule.effectiveTo() == null ? "무기한" : rule.effectiveTo()) + "]";
    }

    /** 해당 날짜에 적용되는 규칙. 겹침이 없으므로 최대 하나다. */
    public Optional<WorkRule> ruleFor(LocalDate date) {
        // 규칙 수는 한 자릿수가 보통이라 선형 탐색으로 충분하다.
        // effectiveFrom 오름차순이므로 시작일이 지난 시점에서 멈춘다.
        for (int i = rules.size() - 1; i >= 0; i--) {
            WorkRule rule = rules.get(i);
            if (rule.effectiveFrom().isAfter(date)) {
                continue;
            }
            return rule.covers(date) ? Optional.of(rule) : Optional.empty();
        }
        return Optional.empty();
    }

    /** 가장 나중에 시작하는 규칙. 설정 화면에서 "현재 패턴"을 보여줄 때 쓴다. */
    public Optional<WorkRule> latest() {
        return rules.isEmpty() ? Optional.empty() : Optional.of(rules.get(rules.size() - 1));
    }

    public List<WorkRule> asList() {
        return rules;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public int size() {
        return rules.size();
    }
}
