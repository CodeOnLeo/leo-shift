package io.github.codeonleo.leoshift.schedule.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.codeonleo.leoshift.schedule.ResolvedDay;
import io.github.codeonleo.leoshift.schedule.ScheduleResolver;
import io.github.codeonleo.leoshift.schedule.WorkRule;
import io.github.codeonleo.leoshift.schedule.WorkRuleSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("PatternPresets — 프리셋 딕셔너리")
class PatternPresetsTest {

    private static final PatternPresets PRESETS = PatternPresets.load();

    static Stream<PatternPreset> allPresets() {
        return PRESETS.all().stream();
    }

    static Stream<PatternPreset> teamPresets() {
        return PRESETS.all().stream().filter(PatternPreset::hasTeams);
    }

    @Test
    @DisplayName("기본 리소스가 로드된다")
    void loads() {
        assertThat(PRESETS.size()).isGreaterThanOrEqualTo(10);
        assertThat(PRESETS.byCategory(PatternPreset.Category.REGULAR)).isNotEmpty();
        assertThat(PRESETS.byCategory(PatternPreset.Category.SHIFT)).isNotEmpty();
        assertThat(PRESETS.commonScheduleTypes()).isNotEmpty();
    }

    @Test
    @DisplayName("id로 찾을 수 있고 없는 id는 예외")
    void lookup() {
        assertThat(PRESETS.byId("kr.shift.4team3shift")).isPresent();
        assertThat(PRESETS.byId("없는프리셋")).isEmpty();
        assertThatThrownBy(() -> PRESETS.require("없는프리셋"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("모든 프리셋이 지켜야 할 것")
    class Invariants {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.codeonleo.leoshift.schedule.preset.PatternPresetsTest#allPresets")
        @DisplayName("시퀀스로 근무 규칙을 만들 수 있다")
        void buildsWorkRule(PatternPreset preset) {
            WorkRule rule = preset.toWorkRule(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2));
            assertThat(rule.cycleLength()).isEqualTo(preset.cycleLength());
            assertThat(rule.sequence()).isEqualTo(preset.sequenceFor(null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.codeonleo.leoshift.schedule.preset.PatternPresetsTest#allPresets")
        @DisplayName("최소한 하나는 실제로 일하는 날이다")
        void hasWorkingDay(PatternPreset preset) {
            Map<String, ScheduleTypeSpec> types = typeMap(preset);
            long workDays = preset.sequence().stream()
                    .filter(code -> types.get(code).countsAsWork())
                    .count();
            assertThat(workDays).as("근무일 수").isGreaterThan(0);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.codeonleo.leoshift.schedule.preset.PatternPresetsTest#allPresets")
        @DisplayName("적용하면 연차·반차가 함께 만들어진다")
        void includesLeaveTypes(PatternPreset preset) {
            List<String> codes = PRESETS.scheduleTypesFor(preset).stream()
                    .map(ScheduleTypeSpec::code).toList();
            assertThat(codes).contains("ANNUAL", "HALF_AM", "HALF_PM");
            assertThat(codes).doesNotHaveDuplicates();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.codeonleo.leoshift.schedule.preset.PatternPresetsTest#allPresets")
        @DisplayName("자정을 넘는 근무는 crossesMidnight로 표시돼 있다")
        void marksOvernightShifts(PatternPreset preset) {
            for (ScheduleTypeSpec spec : preset.scheduleTypes()) {
                if (spec.category() != ScheduleTypeSpec.Category.WORK) {
                    continue;
                }
                boolean wrapsClock = !spec.endTime().isAfter(spec.startTime());
                assertThat(spec.crossesMidnight())
                        .as("%s / %s (%s~%s)", preset.id(), spec.code(), spec.startTime(), spec.endTime())
                        .isEqualTo(wrapsClock);
            }
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("교대조 — 같은 날 서로 겹치지 않는다")
    class Teams {

        /**
         * 이게 프리셋 데이터의 핵심 검증이다. 조 offset이 잘못 적혀 있으면
         * 같은 날 두 조가 같은 근무를 서거나 아무도 안 서는 구멍이 생긴다.
         */
        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.codeonleo.leoshift.schedule.preset.PatternPresetsTest#teamPresets")
        @DisplayName("주기의 매일, 각 근무를 정확히 한 조씩 선다")
        void everyShiftCoveredExactlyOnce(PatternPreset preset) {
            Map<String, ScheduleTypeSpec> types = typeMap(preset);
            List<String> workCodes = preset.scheduleTypes().stream()
                    .filter(ScheduleTypeSpec::countsAsWork)
                    .map(ScheduleTypeSpec::code)
                    .toList();

            List<List<String>> teamSequences = preset.teams().stream()
                    .map(team -> preset.sequenceFor(team.label()))
                    .toList();

            for (int day = 0; day < preset.cycleLength(); day++) {
                Map<String, Integer> counts = new HashMap<>();
                for (List<String> sequence : teamSequences) {
                    String code = sequence.get(day);
                    if (types.get(code).countsAsWork()) {
                        counts.merge(code, 1, Integer::sum);
                    }
                }
                for (String workCode : workCodes) {
                    assertThat(counts.getOrDefault(workCode, 0))
                            .as("%s / %d일차 / %s 근무 조 수", preset.id(), day, workCode)
                            .isEqualTo(1);
                }
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.codeonleo.leoshift.schedule.preset.PatternPresetsTest#teamPresets")
        @DisplayName("모든 조가 같은 양의 근무를 한다")
        void teamsShareWorkloadEqually(PatternPreset preset) {
            Map<String, ScheduleTypeSpec> types = typeMap(preset);
            List<Long> workDaysPerTeam = preset.teams().stream()
                    .map(team -> preset.sequenceFor(team.label()).stream()
                            .filter(code -> types.get(code).countsAsWork()).count())
                    .toList();
            assertThat(workDaysPerTeam).as("%s 조별 근무일 수", preset.id())
                    .containsOnly(workDaysPerTeam.get(0));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.codeonleo.leoshift.schedule.preset.PatternPresetsTest#teamPresets")
        @DisplayName("조가 서로 다른 시퀀스를 가진다")
        void teamsAreDistinct(PatternPreset preset) {
            List<List<String>> sequences = preset.teams().stream()
                    .map(team -> preset.sequenceFor(team.label()))
                    .toList();
            assertThat(sequences).as("%s", preset.id()).doesNotHaveDuplicates();
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("REGULAR — 요일에 정렬된다")
    class RegularAlignment {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.codeonleo.leoshift.schedule.preset.PatternPresetsTest#regularPresets")
        @DisplayName("아무 날짜로 적용해도 요일이 어긋나지 않는다")
        void anchorSnapsToWeekday(PatternPreset preset) {
            // 한 주 전체를 시작일로 시도해도 결과가 같아야 한다
            List<Map<DayOfWeek, String>> results = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate start = LocalDate.of(2026, 3, 2).plusDays(i); // 월~일
                WorkRule rule = preset.toWorkRule(start, start);
                assertThat(rule.anchorDate().getDayOfWeek())
                        .as("%s / 기준일 요일", preset.id())
                        .isEqualTo(preset.anchorWeekday());

                Map<DayOfWeek, String> byWeekday = new HashMap<>();
                for (int d = 0; d < 7; d++) {
                    LocalDate date = LocalDate.of(2026, 4, 6).plusDays(d); // 임의의 월~일
                    byWeekday.put(date.getDayOfWeek(), rule.codeAt(date));
                }
                results.add(byWeekday);
            }
            assertThat(results).as("%s / 시작일이 달라도 요일별 근무는 같아야 한다", preset.id())
                    .containsOnly(results.get(0));
        }

        @Test
        @DisplayName("주5일은 정확히 토·일만 쉰다")
        void weekday5RestsOnWeekend() {
            PatternPreset preset = PRESETS.require("kr.regular.weekday5");
            WorkRule rule = preset.toWorkRule(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 5));

            LocalDate cursor = LocalDate.of(2026, 3, 1);
            for (int i = 0; i < 90; i++) {
                boolean weekend = cursor.getDayOfWeek() == DayOfWeek.SATURDAY
                        || cursor.getDayOfWeek() == DayOfWeek.SUNDAY;
                assertThat(rule.codeAt(cursor)).as("%s (%s)", cursor, cursor.getDayOfWeek())
                        .isEqualTo(weekend ? "OFF" : "WORK");
                cursor = cursor.plusDays(1);
            }
        }

        @Test
        @DisplayName("격주 토요일은 실제로 2주에 한 번만 근무다")
        void alternateSaturday() {
            PatternPreset preset = PRESETS.require("kr.regular.weekday5.altsat");
            WorkRule rule = preset.toWorkRule(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2));

            List<String> saturdays = new ArrayList<>();
            LocalDate cursor = LocalDate.of(2026, 3, 7); // 토요일
            for (int i = 0; i < 8; i++) {
                saturdays.add(rule.codeAt(cursor));
                cursor = cursor.plusWeeks(1);
            }
            assertThat(saturdays).containsExactly(
                    "WORK", "OFF", "WORK", "OFF", "WORK", "OFF", "WORK", "OFF");

            // 일요일은 언제나 휴무
            LocalDate sunday = LocalDate.of(2026, 3, 8);
            for (int i = 0; i < 8; i++) {
                assertThat(rule.codeAt(sunday)).isEqualTo("OFF");
                sunday = sunday.plusWeeks(1);
            }
        }
    }

    static Stream<PatternPreset> regularPresets() {
        return PRESETS.byCategory(PatternPreset.Category.REGULAR).stream();
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("기준일 역산")
    class AnchorInference {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.codeonleo.leoshift.schedule.preset.PatternPresetsTest#hintedPresets")
        @DisplayName("역산한 기준일로 규칙을 만들면 그날이 실제로 그 근무다")
        void inferredAnchorRoundTrips(PatternPreset preset) {
            LocalDate answer = LocalDate.of(2026, 3, 10);
            String hintCode = preset.anchorHint().code();

            for (TeamOption team : teamsOrNull(preset)) {
                String label = team == null ? null : team.label();
                LocalDate anchor = preset.inferAnchor(answer, label).orElseThrow();
                WorkRule rule = preset.toWorkRule(anchor, anchor, label);

                assertThat(rule.codeAt(answer))
                        .as("%s / %s / 답한 날의 근무", preset.id(), label)
                        .isEqualTo(hintCode);
                // 그 전날은 달라야 한다 (run의 첫날이라는 뜻)
                assertThat(rule.codeAt(answer.minusDays(1)))
                        .as("%s / %s / 전날은 다른 근무여야 한다", preset.id(), label)
                        .isNotEqualTo(hintCode);
            }
        }

        @Test
        @DisplayName("같은 사업장의 여러 조가 같은 기준일로 수렴한다")
        void teamsConvergeOnSameAnchor() {
            PatternPreset preset = PRESETS.require("kr.shift.4team3shift");
            LocalDate trueAnchor = LocalDate.of(2026, 3, 2);

            for (TeamOption team : preset.teams()) {
                WorkRule rule = preset.toWorkRule(trueAnchor, trueAnchor, team.label());
                // 이 조가 실제로 야간을 시작하는 날을 찾는다
                LocalDate nightStart = null;
                for (int i = 0; i < preset.cycleLength(); i++) {
                    LocalDate date = trueAnchor.plusDays(i);
                    if (rule.codeAt(date).equals("N") && !rule.codeAt(date.minusDays(1)).equals("N")) {
                        nightStart = date;
                        break;
                    }
                }
                assertThat(nightStart).as("%s 야간 시작일", team.label()).isNotNull();

                // 그 날짜로 역산하면 원래 기준일이 나와야 한다
                assertThat(preset.inferAnchor(nightStart, team.label()))
                        .as("%s 역산 결과", team.label())
                        .contains(trueAnchor);
            }
        }
    }

    static Stream<PatternPreset> hintedPresets() {
        return PRESETS.all().stream().filter(p -> p.anchorHint() != null);
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("엔진과의 연결")
    class EngineIntegration {

        @Test
        @DisplayName("프리셋으로 만든 규칙을 해석기가 그대로 쓴다")
        void feedsResolver() {
            PatternPreset preset = PRESETS.require("kr.shift.3team2shift");
            LocalDate anchor = LocalDate.of(2026, 3, 2);
            WorkRule rule = preset.toWorkRule(anchor, anchor, "2조");

            List<ResolvedDay> days = ScheduleResolver.ofRules(WorkRuleSet.of(rule))
                    .resolveRange(anchor, anchor.plusDays(5));

            assertThat(days).hasSize(6);
            assertThat(days.stream().map(ResolvedDay::code))
                    .containsExactlyElementsOf(preset.sequenceFor("2조"));
        }

        @Test
        @DisplayName("프리셋을 바꿔도 이미 만들어진 규칙은 영향받지 않는다")
        void snapshotSemantics() {
            PatternPreset preset = PRESETS.require("kr.regular.weekday5");
            WorkRule rule = preset.toWorkRule(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2));

            List<String> copied = rule.sequence();
            assertThat(copied).isEqualTo(preset.sequence());
            // 규칙의 시퀀스는 불변 복사본이다
            assertThatThrownBy(() -> copied.set(0, "OFF"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("아직 저장 전이므로 id가 없다")
        void notPersistedYet() {
            WorkRule rule = PRESETS.require("kr.regular.weekday5")
                    .toWorkRule(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2));
            assertThat(rule.id()).isNull();
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("잘못된 정의를 거부한다")
    class Rejections {

        @Test
        void 시퀀스가_정의되지_않은_코드를_쓰면_거부() {
            assertThatThrownBy(() -> load("""
                    {"presets":[{"id":"x","name":"x","category":"SHIFT",
                      "sequence":["D","ZZZ"],
                      "scheduleTypes":[{"code":"D","name":"주간","category":"WORK",
                        "startTime":"09:00","endTime":"18:00"}]}]}"""))
                    .hasMessageContaining("정의되지 않은 코드");
        }

        @Test
        void 조_offset이_주기를_넘으면_거부() {
            assertThatThrownBy(() -> load("""
                    {"presets":[{"id":"x","name":"x","category":"SHIFT",
                      "sequence":["D","O"],
                      "teams":[{"label":"1조","offset":5}],
                      "scheduleTypes":[{"code":"D","name":"주간","category":"WORK",
                        "startTime":"09:00","endTime":"18:00"},
                        {"code":"O","name":"휴무","category":"OFF"}]}]}"""))
                    .hasMessageContaining("offset이 주기를 넘는다");
        }

        @Test
        void REGULAR인데_주기가_7의_배수가_아니면_거부() {
            assertThatThrownBy(() -> load("""
                    {"presets":[{"id":"x","name":"x","category":"REGULAR","anchorWeekday":"MONDAY",
                      "sequence":["WORK","OFF"],
                      "scheduleTypes":[{"code":"WORK","name":"근무","category":"WORK",
                        "startTime":"09:00","endTime":"18:00"},
                        {"code":"OFF","name":"휴무","category":"OFF"}]}]}"""))
                    .hasMessageContaining("7의 배수");
        }

        @Test
        void WORK인데_시간이_없으면_거부() {
            assertThatThrownBy(() -> load("""
                    {"presets":[{"id":"x","name":"x","category":"SHIFT",
                      "sequence":["D"],
                      "scheduleTypes":[{"code":"D","name":"주간","category":"WORK"}]}]}"""))
                    .hasMessageContaining("시작·종료 시각");
        }

        @Test
        void 소문자_코드는_거부() {
            assertThatThrownBy(() -> load("""
                    {"presets":[{"id":"x","name":"x","category":"SHIFT",
                      "sequence":["d"],
                      "scheduleTypes":[{"code":"d","name":"주간","category":"WORK",
                        "startTime":"09:00","endTime":"18:00"}]}]}"""))
                    .hasMessageContaining("대문자");
        }

        @Test
        void anchorHint가_없는_코드를_가리키면_거부() {
            assertThatThrownBy(() -> load("""
                    {"presets":[{"id":"x","name":"x","category":"SHIFT",
                      "sequence":["D"],
                      "anchorHint":{"code":"N","question":"?"},
                      "scheduleTypes":[{"code":"D","name":"주간","category":"WORK",
                        "startTime":"09:00","endTime":"18:00"}]}]}"""))
                    .hasMessageContaining("시퀀스에 없는 코드");
        }

        private static PatternPresets load(String json) {
            return PatternPresets.from(new java.io.ByteArrayInputStream(json.getBytes()));
        }
    }

    // ------------------------------------------------------------------

    private static Map<String, ScheduleTypeSpec> typeMap(PatternPreset preset) {
        Map<String, ScheduleTypeSpec> map = new HashMap<>();
        preset.scheduleTypes().forEach(spec -> map.put(spec.code(), spec));
        return map;
    }

    private static List<TeamOption> teamsOrNull(PatternPreset preset) {
        return preset.hasTeams() ? preset.teams() : java.util.Collections.singletonList(null);
    }
}
