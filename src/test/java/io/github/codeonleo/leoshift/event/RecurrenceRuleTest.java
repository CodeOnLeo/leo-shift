package io.github.codeonleo.leoshift.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RecurrenceRule — 반복 전개")
class RecurrenceRuleTest {

    /** 2026-03-03은 화요일이다. */
    private static final LocalDateTime TUESDAY_2030 = LocalDateTime.of(2026, 3, 3, 20, 30);

    private static List<String> dates(List<LocalDateTime> times) {
        return times.stream().map(at -> at.toLocalDate().toString()).toList();
    }

    @Nested
    @DisplayName("매주")
    class Weekly {

        @Test
        @DisplayName("요일을 여러 개 지정하면 한 주에 여러 번 나온다")
        void multipleDays() {
            RecurrenceRule rule = RecurrenceRule.weekly(1, Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));

            List<LocalDateTime> found = rule.occurrences(
                    TUESDAY_2030,
                    LocalDateTime.of(2026, 3, 1, 0, 0),
                    LocalDateTime.of(2026, 3, 15, 0, 0),
                    null);

            assertThat(dates(found)).containsExactly(
                    "2026-03-03", "2026-03-05", "2026-03-10", "2026-03-12");
            assertThat(found).allSatisfy(at -> assertThat(at.toLocalTime().toString()).isEqualTo("20:30"));
        }

        @Test
        @DisplayName("격주는 한 주 건너뛴다")
        void interval() {
            RecurrenceRule rule = RecurrenceRule.weekly(2, Set.of(DayOfWeek.TUESDAY));

            List<LocalDateTime> found = rule.occurrences(
                    TUESDAY_2030,
                    LocalDateTime.of(2026, 3, 1, 0, 0),
                    LocalDateTime.of(2026, 4, 1, 0, 0),
                    null);

            assertThat(dates(found)).containsExactly("2026-03-03", "2026-03-17", "2026-03-31");
        }

        @Test
        @DisplayName("요일을 지정하지 않으면 시작한 요일로 반복한다")
        void defaultsToStartWeekday() {
            RecurrenceRule rule = RecurrenceRule.weekly(1, Set.of());

            List<LocalDateTime> found = rule.occurrences(
                    TUESDAY_2030,
                    LocalDateTime.of(2026, 3, 1, 0, 0),
                    LocalDateTime.of(2026, 3, 20, 0, 0),
                    null);

            assertThat(dates(found)).containsExactly("2026-03-03", "2026-03-10", "2026-03-17");
        }

        @Test
        @DisplayName("시작 이전 요일은 첫 주에서 빠진다")
        void skipsBeforeSeriesStart() {
            // 화요일에 시작했지만 월·화 반복이면 첫 주의 월요일은 이미 지났다
            RecurrenceRule rule = RecurrenceRule.weekly(1, Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

            List<LocalDateTime> found = rule.occurrences(
                    TUESDAY_2030,
                    LocalDateTime.of(2026, 3, 1, 0, 0),
                    LocalDateTime.of(2026, 3, 12, 0, 0),
                    null);

            assertThat(dates(found)).containsExactly("2026-03-03", "2026-03-09", "2026-03-10");
        }
    }

    @Nested
    @DisplayName("횟수와 종료")
    class Limits {

        @Test
        @DisplayName("COUNT는 조회 구간이 아니라 시리즈 시작부터 센다")
        void countsFromSeriesStart() {
            RecurrenceRule rule = new RecurrenceRule(
                    RecurrenceRule.Frequency.WEEKLY, 1, Set.of(DayOfWeek.TUESDAY), 3);

            // 창을 2주차부터 열어도 총 3회라는 사실은 변하지 않는다.
            // 창 기준으로 세면 반복이 영원히 끝나지 않는다.
            List<LocalDateTime> found = rule.occurrences(
                    TUESDAY_2030,
                    LocalDateTime.of(2026, 3, 9, 0, 0),
                    LocalDateTime.of(2026, 5, 1, 0, 0),
                    null);

            assertThat(dates(found)).containsExactly("2026-03-10", "2026-03-17");
        }

        @Test
        @DisplayName("종료 시각 이후는 나오지 않는다")
        void until() {
            RecurrenceRule rule = RecurrenceRule.weekly(1, Set.of(DayOfWeek.TUESDAY));

            List<LocalDateTime> found = rule.occurrences(
                    TUESDAY_2030,
                    LocalDateTime.of(2026, 3, 1, 0, 0),
                    LocalDateTime.of(2026, 5, 1, 0, 0),
                    LocalDateTime.of(2026, 3, 17, 23, 59));

            assertThat(dates(found)).containsExactly("2026-03-03", "2026-03-10", "2026-03-17");
        }
    }

    @Nested
    @DisplayName("매월 · 매년")
    class Calendar {

        @Test
        @DisplayName("31일 반복은 31일이 없는 달을 건너뛴다")
        void skipsShortMonths() {
            RecurrenceRule rule = new RecurrenceRule(
                    RecurrenceRule.Frequency.MONTHLY, 1, Set.of(), null);

            List<LocalDateTime> found = rule.occurrences(
                    LocalDateTime.of(2026, 1, 31, 9, 0),
                    LocalDateTime.of(2026, 1, 1, 0, 0),
                    LocalDateTime.of(2026, 6, 1, 0, 0),
                    null);

            // 2월과 4월에는 31일이 없다. 30일로 당기면 사용자가 정하지 않은 날에 생긴다.
            assertThat(dates(found)).containsExactly(
                    "2026-01-31", "2026-03-31", "2026-05-31");
        }

        @Test
        @DisplayName("2월 29일 반복은 윤년에만 나온다")
        void leapDay() {
            RecurrenceRule rule = new RecurrenceRule(
                    RecurrenceRule.Frequency.YEARLY, 1, Set.of(), null);

            List<LocalDateTime> found = rule.occurrences(
                    LocalDateTime.of(2024, 2, 29, 9, 0),
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2033, 1, 1, 0, 0),
                    null);

            assertThat(dates(found)).containsExactly("2024-02-29", "2028-02-29", "2032-02-29");
        }

        @Test
        @DisplayName("매일 반복은 간격만큼 건너뛴다")
        void daily() {
            RecurrenceRule rule = new RecurrenceRule(
                    RecurrenceRule.Frequency.DAILY, 3, Set.of(), null);

            List<LocalDateTime> found = rule.occurrences(
                    LocalDateTime.of(2026, 3, 1, 7, 0),
                    LocalDateTime.of(2026, 3, 1, 0, 0),
                    LocalDateTime.of(2026, 3, 11, 0, 0),
                    null);

            assertThat(dates(found)).containsExactly(
                    "2026-03-01", "2026-03-04", "2026-03-07", "2026-03-10");
        }
    }

    @Nested
    @DisplayName("문자열")
    class Text {

        @Test
        @DisplayName("표준 RRULE로 쓰고 다시 읽는다")
        void roundTrip() {
            RecurrenceRule rule = new RecurrenceRule(
                    RecurrenceRule.Frequency.WEEKLY, 2,
                    Set.of(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY), 10);

            assertThat(rule.toRRule()).isEqualTo("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;COUNT=10");
            assertThat(RecurrenceRule.parse(rule.toRRule())).isEqualTo(rule);
        }

        @Test
        @DisplayName("UNTIL은 무시한다 — 종료는 컬럼이 담당한다")
        void ignoresUntil() {
            RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;UNTIL=20260401T000000Z");

            assertThat(rule.frequency()).isEqualTo(RecurrenceRule.Frequency.WEEKLY);
            assertThat(rule.toRRule()).isEqualTo("FREQ=WEEKLY");
        }

        @Test
        @DisplayName("지원하지 않는 규칙은 조용히 넘기지 않고 거부한다")
        void rejectsUnsupported() {
            // 무시하면 "매월 셋째 주 화요일"이 저장은 되는데 매월 1일에 도는 규칙이 된다
            assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=MONTHLY;BYSETPOS=3;BYDAY=TU"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=NEVER"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("반복 주기");

            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceRule.Frequency.MONTHLY, 1, Set.of(DayOfWeek.TUESDAY), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("매주");
        }

        @Test
        @DisplayName("간격과 횟수는 1 이상이어야 한다")
        void rejectsNonPositive() {
            assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=DAILY;INTERVAL=0"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=DAILY;COUNT=0"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("창을 벗어난 시작일이어도 창 안만 돌려준다")
    void windowBounded() {
        RecurrenceRule rule = RecurrenceRule.weekly(1, Set.of(DayOfWeek.TUESDAY));

        List<LocalDateTime> found = rule.occurrences(
                LocalDateTime.of(2020, 1, 7, 20, 30),
                LocalDateTime.of(2026, 3, 1, 0, 0),
                LocalDateTime.of(2026, 3, 15, 0, 0),
                null);

        assertThat(dates(found)).containsExactly("2026-03-03", "2026-03-10");
        assertThat(LocalDate.parse(dates(found).get(0)).getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
    }
}
