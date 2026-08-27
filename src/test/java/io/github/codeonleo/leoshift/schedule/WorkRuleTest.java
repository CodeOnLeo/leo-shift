package io.github.codeonleo.leoshift.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("WorkRule — 주기 계산")
class WorkRuleTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5); // 월요일
    private static final List<String> WEEKDAYS =
            List.of("WORK", "WORK", "WORK", "WORK", "WORK", "OFF", "OFF");
    private static final List<String> FOUR_TEAM_THREE_SHIFT =
            List.of("D", "D", "D", "A", "A", "A", "N", "N", "N", "O", "O", "O");

    private static WorkRule weekly() {
        return WorkRule.openEnded(1L, MONDAY, WEEKDAYS, MONDAY);
    }

    @Test
    @DisplayName("기준일은 시퀀스의 0번이다")
    void anchorIsIndexZero() {
        assertThat(weekly().indexAt(MONDAY)).isZero();
        assertThat(weekly().codeAt(MONDAY)).isEqualTo("WORK");
    }

    @ParameterizedTest(name = "{0}일 뒤 = {1}")
    @CsvSource({"0,WORK", "1,WORK", "4,WORK", "5,OFF", "6,OFF", "7,WORK", "12,OFF", "13,OFF", "14,WORK"})
    @DisplayName("주기가 반복된다")
    void cycles(int plusDays, String expected) {
        assertThat(weekly().codeAt(MONDAY.plusDays(plusDays))).isEqualTo(expected);
    }

    @Test
    @DisplayName("주기 7의 규칙은 요일 규칙과 같다 — 1년 내내 토·일만 쉰다")
    void weeklyRuleEqualsDayOfWeekRule() {
        WorkRule rule = weekly();
        LocalDate cursor = MONDAY;
        for (int i = 0; i < 365; i++) {
            boolean weekend = cursor.getDayOfWeek() == DayOfWeek.SATURDAY
                    || cursor.getDayOfWeek() == DayOfWeek.SUNDAY;
            assertThat(rule.codeAt(cursor))
                    .as("%s (%s)", cursor, cursor.getDayOfWeek())
                    .isEqualTo(weekend ? "OFF" : "WORK");
            cursor = cursor.plusDays(1);
        }
    }

    @Test
    @DisplayName("기준일 이전 날짜도 올바르게 계산된다 — 음수 나머지 처리")
    void handlesDatesBeforeAnchor() {
        WorkRule rule = weekly();
        // 기준일 하루 전은 일요일이므로 시퀀스의 마지막(OFF)
        assertThat(rule.indexAt(MONDAY.minusDays(1))).isEqualTo(6);
        assertThat(rule.codeAt(MONDAY.minusDays(1))).isEqualTo("OFF");
        assertThat(rule.codeAt(MONDAY.minusDays(2))).isEqualTo("OFF"); // 토
        assertThat(rule.codeAt(MONDAY.minusDays(3))).isEqualTo("WORK"); // 금
        assertThat(rule.codeAt(MONDAY.minusDays(7))).isEqualTo("WORK"); // 정확히 한 주 전 월요일
    }

    @Test
    @DisplayName("아주 먼 과거·미래에서도 인덱스가 범위를 벗어나지 않는다")
    void indexStaysInRange() {
        WorkRule rule = WorkRule.openEnded(1L, MONDAY, FOUR_TEAM_THREE_SHIFT, MONDAY);
        for (LocalDate date : List.of(
                LocalDate.of(1900, 1, 1), LocalDate.of(2000, 2, 29),
                LocalDate.of(2099, 12, 31), LocalDate.of(2400, 6, 15))) {
            int index = rule.indexAt(date);
            assertThat(index).as("%s", date).isBetween(0, rule.cycleLength() - 1);
        }
    }

    @Test
    @DisplayName("윤일을 건너뛰지 않는다")
    void countsLeapDay() {
        WorkRule rule = WorkRule.openEnded(1L, LocalDate.of(2024, 2, 28), List.of("A", "B"), LocalDate.of(2024, 1, 1));
        assertThat(rule.codeAt(LocalDate.of(2024, 2, 28))).isEqualTo("A");
        assertThat(rule.codeAt(LocalDate.of(2024, 2, 29))).isEqualTo("B"); // 윤일
        assertThat(rule.codeAt(LocalDate.of(2024, 3, 1))).isEqualTo("A");
    }

    @Test
    @DisplayName("주기 1이면 매일 같은 코드")
    void singleDayCycle() {
        WorkRule rule = WorkRule.openEnded(1L, MONDAY, List.of("ON"), MONDAY);
        assertThat(rule.codeAt(MONDAY)).isEqualTo("ON");
        assertThat(rule.codeAt(MONDAY.plusDays(1000))).isEqualTo("ON");
        assertThat(rule.codeAt(MONDAY.minusDays(1000))).isEqualTo("ON");
    }

    @Test
    @DisplayName("격일제 — 주기 2")
    void everyOtherDay() {
        WorkRule rule = WorkRule.openEnded(1L, MONDAY, List.of("ON", "OFF"), MONDAY);
        assertThat(rule.codeAt(MONDAY)).isEqualTo("ON");
        assertThat(rule.codeAt(MONDAY.plusDays(1))).isEqualTo("OFF");
        assertThat(rule.codeAt(MONDAY.plusDays(2))).isEqualTo("ON");
    }

    @Test
    @DisplayName("4조 3교대 — 12일 주기가 정확히 돈다")
    void fourTeamThreeShift() {
        WorkRule rule = WorkRule.openEnded(1L, MONDAY, FOUR_TEAM_THREE_SHIFT, MONDAY);
        for (int i = 0; i < 12; i++) {
            assertThat(rule.codeAt(MONDAY.plusDays(i))).isEqualTo(FOUR_TEAM_THREE_SHIFT.get(i));
            assertThat(rule.codeAt(MONDAY.plusDays(i + 12))).isEqualTo(FOUR_TEAM_THREE_SHIFT.get(i));
            assertThat(rule.codeAt(MONDAY.plusDays(i + 120))).isEqualTo(FOUR_TEAM_THREE_SHIFT.get(i));
        }
    }

    @Test
    @DisplayName("유효기간은 양 끝을 포함한다")
    void coversIsInclusive() {
        WorkRule rule = new WorkRule(1L, MONDAY, WEEKDAYS,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        assertThat(rule.covers(LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(rule.covers(LocalDate.of(2026, 3, 1))).isTrue();
        assertThat(rule.covers(LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(rule.covers(LocalDate.of(2026, 4, 1))).isFalse();
    }

    @Test
    @DisplayName("무기한 규칙은 시작일 이후 전부 포함한다")
    void openEndedCoversForever() {
        WorkRule rule = WorkRule.openEnded(1L, MONDAY, WEEKDAYS, LocalDate.of(2026, 3, 1));
        assertThat(rule.covers(LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(rule.covers(LocalDate.of(2099, 1, 1))).isTrue();
    }

    @Nested
    @DisplayName("입력 검증")
    class Validation {

        @Test
        void 빈_시퀀스는_거부한다() {
            assertThatThrownBy(() -> WorkRule.openEnded(1L, MONDAY, List.of(), MONDAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("최소 1개");
        }

        @Test
        void 시퀀스에_빈_코드가_있으면_거부한다() {
            assertThatThrownBy(() -> WorkRule.openEnded(1L, MONDAY, java.util.Arrays.asList("D", " "), MONDAY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("빈 코드");
        }

        @Test
        void 종료일이_시작일보다_빠르면_거부한다() {
            assertThatThrownBy(() -> new WorkRule(1L, MONDAY, WEEKDAYS,
                    LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("빠르다");
        }

        @Test
        void 시퀀스는_불변이다() {
            List<String> mutable = new java.util.ArrayList<>(List.of("D", "O"));
            WorkRule rule = WorkRule.openEnded(1L, MONDAY, mutable, MONDAY);
            mutable.set(0, "N"); // 원본을 바꿔도
            assertThat(rule.codeAt(MONDAY)).isEqualTo("D"); // 규칙은 영향받지 않는다
            assertThatThrownBy(() -> rule.sequence().set(0, "N"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("프리셋 — 조 선택(rotate)")
    class Rotation {

        @Test
        @DisplayName("4조 3교대의 각 조는 기준 시퀀스를 회전한 것이다")
        void teamOffsets() {
            assertThat(WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, 0)).isEqualTo(FOUR_TEAM_THREE_SHIFT);
            assertThat(WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, 3))
                    .containsExactly("A", "A", "A", "N", "N", "N", "O", "O", "O", "D", "D", "D");
            assertThat(WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, 6))
                    .containsExactly("N", "N", "N", "O", "O", "O", "D", "D", "D", "A", "A", "A");
            assertThat(WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, 9))
                    .containsExactly("O", "O", "O", "D", "D", "D", "A", "A", "A", "N", "N", "N");
        }

        @Test
        @DisplayName("같은 날 4개 조는 서로 다른 근무를 한다")
        void teamsDoNotCollide() {
            List<Integer> offsets = List.of(0, 3, 6, 9);
            List<String> codesToday = offsets.stream()
                    .map(offset -> WorkRule.openEnded(1L, MONDAY,
                            WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, offset), MONDAY).codeAt(MONDAY))
                    .toList();
            assertThat(codesToday).containsExactly("D", "A", "N", "O");
        }

        @Test
        @DisplayName("주기를 넘는 offset과 음수 offset도 감싼다")
        void wrapsAround() {
            assertThat(WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, 12)).isEqualTo(FOUR_TEAM_THREE_SHIFT);
            assertThat(WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, 15))
                    .isEqualTo(WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, 3));
            assertThat(WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, -3))
                    .isEqualTo(WorkRule.rotate(FOUR_TEAM_THREE_SHIFT, 9));
        }
    }

    @Nested
    @DisplayName("온보딩 — 기준일 역산")
    class AnchorInference {

        @Test
        @DisplayName("'야간을 시작한 날'로 기준일을 역산한다")
        void inferFromFirstNightShift() {
            // 4조 3교대에서 N은 6번 위치에서 시작한다.
            LocalDate firstNight = LocalDate.of(2026, 3, 10);
            Optional<LocalDate> anchor =
                    WorkRule.anchorFromFirstOccurrence(FOUR_TEAM_THREE_SHIFT, "N", firstNight);

            assertThat(anchor).contains(firstNight.minusDays(6));

            // 역산한 기준일로 규칙을 만들면 그날이 실제로 야간 첫날이 된다
            WorkRule rule = WorkRule.openEnded(1L, anchor.orElseThrow(), FOUR_TEAM_THREE_SHIFT, anchor.orElseThrow());
            assertThat(rule.codeAt(firstNight)).isEqualTo("N");
            assertThat(rule.codeAt(firstNight.minusDays(1))).isEqualTo("A"); // 전날은 아직 오후
            assertThat(rule.codeAt(firstNight.plusDays(2))).isEqualTo("N");
            assertThat(rule.codeAt(firstNight.plusDays(3))).isEqualTo("O"); // 야간 3일 뒤 휴무
        }

        @Test
        @DisplayName("run 시작은 순환 기준으로 찾는다")
        void runStartWrapsAround() {
            // [N,O,O,N,N] 에서 index 0은 index 4와 이어지므로 run 시작이 아니다
            assertThat(WorkRule.firstRunStart(List.of("N", "O", "O", "N", "N"), "N"))
                    .isEqualTo(OptionalInt.of(3));
            assertThat(WorkRule.firstRunStart(List.of("N", "O", "O", "N", "N"), "O"))
                    .isEqualTo(OptionalInt.of(1));
        }

        @Test
        @DisplayName("시퀀스 전체가 같은 코드면 0을 돌려준다")
        void allSameCode() {
            assertThat(WorkRule.firstRunStart(List.of("D", "D", "D"), "D")).isEqualTo(OptionalInt.of(0));
        }

        @Test
        @DisplayName("없는 코드는 빈 값")
        void unknownCode() {
            assertThat(WorkRule.firstRunStart(FOUR_TEAM_THREE_SHIFT, "ZZZ")).isEmpty();
            assertThat(WorkRule.anchorFromFirstOccurrence(FOUR_TEAM_THREE_SHIFT, "ZZZ", MONDAY)).isEmpty();
        }
    }
}
