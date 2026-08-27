package io.github.codeonleo.leoshift.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.codeonleo.leoshift.schedule.ResolvedDay.Source;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ScheduleResolver — 규칙·휴가·예외 해석")
class ScheduleResolverTest {

    private static final LocalDate MON = LocalDate.of(2026, 1, 5);
    private static final List<String> WEEKDAYS =
            List.of("WORK", "WORK", "WORK", "WORK", "WORK", "OFF", "OFF");

    private static WorkRuleSet weeklyFrom(LocalDate from) {
        return WorkRuleSet.of(WorkRule.openEnded(1L, MON, WEEKDAYS, from));
    }

    private static ScheduleResolver resolver(WorkRuleSet rules,
                                             List<LeavePeriod> leaves,
                                             List<DayOverride> overrides) {
        return new ScheduleResolver(rules, leaves, overrides);
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("규칙만 있으면 규칙대로 나온다")
    void ruleOnly() {
        ScheduleResolver r = ScheduleResolver.ofRules(weeklyFrom(MON));
        ResolvedDay day = r.resolve(MON);
        assertThat(day.code()).isEqualTo("WORK");
        assertThat(day.source()).isEqualTo(Source.RULE);
        assertThat(day.sourceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("적용되는 규칙이 없으면 코드가 없다")
    void noRuleMeansNoCode() {
        ScheduleResolver r = ScheduleResolver.ofRules(WorkRuleSet.empty());
        ResolvedDay day = r.resolve(MON);
        assertThat(day.hasCode()).isFalse();
        assertThat(day.source()).isEqualTo(Source.NONE);
    }

    @Test
    @DisplayName("규칙 시작일 이전은 코드가 없다 — 과거로 소급 적용하지 않는다")
    void doesNotApplyBeforeEffectiveFrom() {
        ScheduleResolver r = ScheduleResolver.ofRules(weeklyFrom(LocalDate.of(2026, 3, 1)));
        assertThat(r.resolve(LocalDate.of(2026, 2, 28)).source()).isEqualTo(Source.NONE);
        assertThat(r.resolve(LocalDate.of(2026, 3, 1)).source()).isEqualTo(Source.RULE);
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("계층 우선순위")
    class Layering {

        @Test
        @DisplayName("휴가가 규칙을 덮는다")
        void leaveBeatsRule() {
            ScheduleResolver r = resolver(weeklyFrom(MON),
                    List.of(LeavePeriod.of(10L, MON, MON.plusDays(2), "ANNUAL")),
                    List.of());

            assertThat(r.resolve(MON).code()).isEqualTo("ANNUAL");
            assertThat(r.resolve(MON).source()).isEqualTo(Source.LEAVE);
            assertThat(r.resolve(MON).sourceId()).isEqualTo(10L);
            // 휴가가 끝나면 다시 규칙으로 돌아온다
            assertThat(r.resolve(MON.plusDays(3)).code()).isEqualTo("WORK");
            assertThat(r.resolve(MON.plusDays(3)).source()).isEqualTo(Source.RULE);
        }

        @Test
        @DisplayName("예외가 휴가를 덮는다")
        void overrideBeatsLeave() {
            ScheduleResolver r = resolver(weeklyFrom(MON),
                    List.of(LeavePeriod.of(10L, MON, MON.plusDays(4), "ANNUAL")),
                    List.of(DayOverride.ofCode(20L, MON.plusDays(1), "WORK")));

            assertThat(r.resolve(MON).source()).isEqualTo(Source.LEAVE);
            assertThat(r.resolve(MON.plusDays(1)).code()).isEqualTo("WORK");
            assertThat(r.resolve(MON.plusDays(1)).source()).isEqualTo(Source.OVERRIDE);
            assertThat(r.resolve(MON.plusDays(2)).source()).isEqualTo(Source.LEAVE);
        }

        @Test
        @DisplayName("휴가 기간은 양 끝을 포함한다")
        void leaveRangeInclusive() {
            ScheduleResolver r = resolver(weeklyFrom(MON),
                    List.of(LeavePeriod.of(10L, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20), "ANNUAL")),
                    List.of());

            assertThat(r.resolve(LocalDate.of(2026, 8, 14)).source()).isNotEqualTo(Source.LEAVE);
            assertThat(r.resolve(LocalDate.of(2026, 8, 15)).source()).isEqualTo(Source.LEAVE);
            assertThat(r.resolve(LocalDate.of(2026, 8, 20)).source()).isEqualTo(Source.LEAVE);
            assertThat(r.resolve(LocalDate.of(2026, 8, 21)).source()).isNotEqualTo(Source.LEAVE);
        }

        @Test
        @DisplayName("메모만 있는 예외는 코드를 덮지 않고 메모만 얹는다")
        void noteOnlyOverrideKeepsCode() {
            ScheduleResolver r = resolver(weeklyFrom(MON),
                    List.of(),
                    List.of(DayOverride.ofNote(20L, MON, "팀 회식")));

            ResolvedDay day = r.resolve(MON);
            assertThat(day.code()).isEqualTo("WORK");           // 규칙 유지
            assertThat(day.source()).isEqualTo(Source.RULE);
            assertThat(day.note()).isEqualTo("팀 회식");
            assertThat(day.hasNote()).isTrue();
        }

        @Test
        @DisplayName("코드를 덮는 예외의 메모도 함께 나온다")
        void overrideCarriesNote() {
            ScheduleResolver r = resolver(weeklyFrom(MON), List.of(),
                    List.of(new DayOverride(20L, MON, "NIGHT", "대타")));

            ResolvedDay day = r.resolve(MON);
            assertThat(day.code()).isEqualTo("NIGHT");
            assertThat(day.source()).isEqualTo(Source.OVERRIDE);
            assertThat(day.note()).isEqualTo("대타");
        }

        @Test
        @DisplayName("규칙이 없어도 휴가와 메모는 나온다")
        void worksWithoutAnyRule() {
            ScheduleResolver r = resolver(WorkRuleSet.empty(),
                    List.of(LeavePeriod.single(10L, MON, "ANNUAL")),
                    List.of(DayOverride.ofNote(20L, MON.plusDays(1), "메모")));

            assertThat(r.resolve(MON).code()).isEqualTo("ANNUAL");
            assertThat(r.resolve(MON.plusDays(1)).hasCode()).isFalse();
            assertThat(r.resolve(MON.plusDays(1)).note()).isEqualTo("메모");
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("규칙 승계 — 이전 구현의 버그 회귀 테스트")
    class RuleSuccession {

        /**
         * 이전 구현은 월 달력을 그릴 때 그리드 마지막 날 기준으로 규칙을 한 번만 조회해
         * 42일 전체에 적용했다. 반면 날짜 상세 화면은 날짜마다 조회했다.
         * 그래서 패턴을 월 중간부터 바꾸면 두 화면이 서로 다른 근무를 보여줬다.
         */
        @Test
        @DisplayName("패턴을 월 중간에 바꿔도 날짜마다 맞는 규칙이 적용된다")
        void appliesCorrectRulePerDay() {
            LocalDate switchDay = LocalDate.of(2026, 3, 16);
            WorkRuleSet rules = WorkRuleSet.of(
                    new WorkRule(1L, MON, List.of("OLD"), LocalDate.of(2026, 1, 1), switchDay.minusDays(1)),
                    WorkRule.openEnded(2L, switchDay, List.of("NEW"), switchDay));

            ScheduleResolver r = ScheduleResolver.ofRules(rules);

            assertThat(r.resolve(LocalDate.of(2026, 3, 1)).code()).isEqualTo("OLD");
            assertThat(r.resolve(switchDay.minusDays(1)).code()).isEqualTo("OLD");
            assertThat(r.resolve(switchDay).code()).isEqualTo("NEW");
            assertThat(r.resolve(LocalDate.of(2026, 3, 31)).code()).isEqualTo("NEW");
        }

        @Test
        @DisplayName("한 달을 통째로 해석해도 경계가 정확하다")
        void wholeMonthKeepsBoundary() {
            LocalDate switchDay = LocalDate.of(2026, 3, 16);
            WorkRuleSet rules = WorkRuleSet.of(
                    new WorkRule(1L, MON, List.of("OLD"), LocalDate.of(2026, 1, 1), switchDay.minusDays(1)),
                    WorkRule.openEnded(2L, switchDay, List.of("NEW"), switchDay));

            List<ResolvedDay> march = ScheduleResolver.ofRules(rules)
                    .resolveRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

            assertThat(march).hasSize(31);
            assertThat(march.stream().filter(d -> "OLD".equals(d.code())).count()).isEqualTo(15);
            assertThat(march.stream().filter(d -> "NEW".equals(d.code())).count()).isEqualTo(16);
        }

        @Test
        @DisplayName("월 달력(42칸)과 날짜 상세가 항상 같은 답을 준다")
        void gridAndDetailAgree() {
            LocalDate switchDay = LocalDate.of(2026, 3, 16);
            WorkRuleSet rules = WorkRuleSet.of(
                    new WorkRule(1L, MON, List.of("A", "B"), LocalDate.of(2026, 1, 1), switchDay.minusDays(1)),
                    WorkRule.openEnded(2L, switchDay, List.of("X", "Y", "Z"), switchDay));

            ScheduleResolver r = ScheduleResolver.ofRules(rules);
            LocalDate gridStart = LocalDate.of(2026, 3, 1);
            List<ResolvedDay> grid = r.resolveRange(gridStart, gridStart.plusDays(41));

            assertThat(grid).hasSize(42);
            for (ResolvedDay fromGrid : grid) {
                ResolvedDay fromDetail = r.resolve(fromGrid.date());
                assertThat(fromDetail).as("%s", fromGrid.date()).isEqualTo(fromGrid);
            }
        }

        @Test
        @DisplayName("규칙 사이에 빈 기간이 있으면 그 기간은 코드가 없다")
        void gapBetweenRules() {
            WorkRuleSet rules = WorkRuleSet.of(
                    new WorkRule(1L, MON, List.of("OLD"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                    WorkRule.openEnded(2L, MON, List.of("NEW"), LocalDate.of(2026, 3, 1)));

            ScheduleResolver r = ScheduleResolver.ofRules(rules);
            assertThat(r.resolve(LocalDate.of(2026, 1, 31)).code()).isEqualTo("OLD");
            assertThat(r.resolve(LocalDate.of(2026, 2, 15)).source()).isEqualTo(Source.NONE);
            assertThat(r.resolve(LocalDate.of(2026, 3, 1)).code()).isEqualTo("NEW");
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("기간 해석")
    class RangeResolution {

        @Test
        @DisplayName("양 끝을 포함한다")
        void inclusive() {
            List<ResolvedDay> days = ScheduleResolver.ofRules(weeklyFrom(MON))
                    .resolveRange(MON, MON.plusDays(6));
            assertThat(days).hasSize(7);
            assertThat(days.get(0).date()).isEqualTo(MON);
            assertThat(days.get(6).date()).isEqualTo(MON.plusDays(6));
        }

        @Test
        @DisplayName("역순 범위는 빈 목록")
        void reversedRangeIsEmpty() {
            assertThat(ScheduleResolver.ofRules(weeklyFrom(MON)).resolveRange(MON.plusDays(3), MON)).isEmpty();
        }

        @Test
        @DisplayName("하루짜리 범위")
        void singleDay() {
            assertThat(ScheduleResolver.ofRules(weeklyFrom(MON)).resolveRange(MON, MON)).hasSize(1);
        }

        @Test
        @DisplayName("한 해를 해석해도 날짜가 빠지거나 중복되지 않는다")
        void fullYear() {
            List<ResolvedDay> days = ScheduleResolver.ofRules(weeklyFrom(LocalDate.of(2024, 1, 1)))
                    .resolveRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
            assertThat(days).hasSize(366); // 2024는 윤년
            assertThat(days.stream().map(ResolvedDay::date).distinct().count()).isEqualTo(366);
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("불변식")
    class Invariants {

        @Test
        @DisplayName("유효기간이 겹치는 규칙은 거부한다")
        void rejectsOverlappingRules() {
            assertThatThrownBy(() -> WorkRuleSet.of(
                    new WorkRule(1L, MON, List.of("A"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
                    WorkRule.openEnded(2L, MON, List.of("B"), LocalDate.of(2026, 3, 1))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("겹친다");
        }

        @Test
        @DisplayName("무기한 규칙 뒤에 다른 규칙을 붙일 수 없다")
        void rejectsRuleAfterOpenEnded() {
            assertThatThrownBy(() -> WorkRuleSet.of(
                    WorkRule.openEnded(1L, MON, List.of("A"), LocalDate.of(2026, 1, 1)),
                    WorkRule.openEnded(2L, MON, List.of("B"), LocalDate.of(2026, 6, 1))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("겹친다");
        }

        @Test
        @DisplayName("맞닿은 규칙은 겹치지 않는다")
        void adjacentRulesAreFine() {
            WorkRuleSet rules = WorkRuleSet.of(
                    new WorkRule(1L, MON, List.of("A"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
                    WorkRule.openEnded(2L, MON, List.of("B"), LocalDate.of(2026, 4, 1)));
            assertThat(rules.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("입력 순서가 달라도 결과가 같다")
        void orderIndependent() {
            WorkRule older = new WorkRule(1L, MON, List.of("A"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
            WorkRule newer = WorkRule.openEnded(2L, MON, List.of("B"), LocalDate.of(2026, 4, 1));

            assertThat(ScheduleResolver.ofRules(WorkRuleSet.of(older, newer)).resolve(LocalDate.of(2026, 5, 1)))
                    .isEqualTo(ScheduleResolver.ofRules(WorkRuleSet.of(newer, older)).resolve(LocalDate.of(2026, 5, 1)));
        }

        @Test
        @DisplayName("같은 날짜에 예외가 둘이면 거부한다")
        void rejectsDuplicateOverrides() {
            assertThatThrownBy(() -> resolver(WorkRuleSet.empty(), List.of(),
                    List.of(DayOverride.ofCode(1L, MON, "A"), DayOverride.ofCode(2L, MON, "B"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("둘 이상");
        }

        @Test
        @DisplayName("휴가가 겹쳐도 항상 같은 답을 준다 — 나중에 시작한 쪽")
        void overlappingLeavesAreDeterministic() {
            List<LeavePeriod> leaves = List.of(
                    LeavePeriod.of(1L, MON, MON.plusDays(5), "ANNUAL"),
                    LeavePeriod.of(2L, MON.plusDays(2), MON.plusDays(3), "SICK"));

            ScheduleResolver forward = resolver(weeklyFrom(MON), leaves, List.of());
            ScheduleResolver reversed = resolver(weeklyFrom(MON), leaves.reversed(), List.of());

            for (int i = 0; i <= 5; i++) {
                LocalDate date = MON.plusDays(i);
                assertThat(forward.resolve(date)).as("%s", date).isEqualTo(reversed.resolve(date));
            }
            assertThat(forward.resolve(MON.plusDays(2)).code()).isEqualTo("SICK");
            assertThat(forward.resolve(MON.plusDays(4)).code()).isEqualTo("ANNUAL");
        }

        @Test
        @DisplayName("null 입력을 빈 값으로 다룬다")
        void tolerantOfNulls() {
            ScheduleResolver r = new ScheduleResolver(null, null, null);
            assertThat(r.resolve(MON).source()).isEqualTo(Source.NONE);
            assertThat(r.resolveRange(null, MON)).isEmpty();
        }
    }
}
