package io.github.codeonleo.leoshift.ics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 구독한 반복 일정 펼치기.
 *
 * <p>이전 구현은 반복을 <b>첫 회만</b> 보여줬다. 매주 회의를 구독하면 첫 주만 달력에
 * 뜨고 그 뒤로는 아무것도 없었다. 여기가 그 자리다.
 */
class IcsExpanderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDate.of(year, month, day).atTime(hour, minute).atZone(SEOUL).toInstant();
    }

    private static IcsEvent event(String rrule, Instant until, Set<Instant> exDates) {
        return new IcsEvent("class@example.com", null, "수업", null, null,
                at(2026, 3, 10, 20, 30), at(2026, 3, 10, 21, 30),
                false, SEOUL, rrule, until, exDates, false);
    }

    @Test
    @DisplayName("매주 반복이 창 안에서 전부 나온다")
    void expandsWeekly() {
        List<IcsExpander.Occurrence> occurrences = IcsExpander.expand(
                List.of(event("FREQ=WEEKLY;BYDAY=TU", null, Set.of())),
                at(2026, 3, 1, 0, 0), at(2026, 4, 1, 0, 0));

        assertThat(occurrences).extracting(IcsExpander.Occurrence::startsAt).containsExactly(
                at(2026, 3, 10, 20, 30),
                at(2026, 3, 17, 20, 30),
                at(2026, 3, 24, 20, 30),
                at(2026, 3, 31, 20, 30));
    }

    @Test
    @DisplayName("UNTIL 뒤로는 나오지 않는다")
    void stopsAtUntil() {
        List<IcsExpander.Occurrence> occurrences = IcsExpander.expand(
                List.of(event("FREQ=WEEKLY;BYDAY=TU", at(2026, 3, 20, 0, 0), Set.of())),
                at(2026, 3, 1, 0, 0), at(2026, 4, 1, 0, 0));

        assertThat(occurrences).extracting(IcsExpander.Occurrence::startsAt).containsExactly(
                at(2026, 3, 10, 20, 30),
                at(2026, 3, 17, 20, 30));
    }

    @Test
    @DisplayName("EXDATE에 걸린 회차는 빠진다")
    void skipsExcluded() {
        List<IcsExpander.Occurrence> occurrences = IcsExpander.expand(
                List.of(event("FREQ=WEEKLY;BYDAY=TU", null, Set.of(at(2026, 3, 17, 20, 30)))),
                at(2026, 3, 1, 0, 0), at(2026, 4, 1, 0, 0));

        assertThat(occurrences).extracting(IcsExpander.Occurrence::startsAt)
                .doesNotContain(at(2026, 3, 17, 20, 30))
                .hasSize(3);
    }

    @Test
    @DisplayName("옮긴 회차는 새 시각에만 나오고 원래 자리는 비운다")
    void appliesOverride() {
        IcsEvent series = event("FREQ=WEEKLY;BYDAY=TU", null, Set.of());
        IcsEvent moved = new IcsEvent("class@example.com", at(2026, 3, 17, 20, 30),
                "보강", null, null,
                at(2026, 3, 18, 20, 30), at(2026, 3, 18, 21, 30),
                false, SEOUL, null, null, Set.of(), false);

        List<IcsExpander.Occurrence> occurrences = IcsExpander.expand(
                List.of(series, moved), at(2026, 3, 1, 0, 0), at(2026, 4, 1, 0, 0));

        assertThat(occurrences).extracting(IcsExpander.Occurrence::startsAt)
                .doesNotContain(at(2026, 3, 17, 20, 30))
                .contains(at(2026, 3, 18, 20, 30));
    }

    @Test
    @DisplayName("DTSTART로 이미 정해지는 BYMONTHDAY는 떼어내고 읽는다")
    void ignoresRedundantByMonthDay() {
        IcsEvent monthly = new IcsEvent("m@example.com", null, "정산", null, null,
                at(2026, 3, 10, 9, 0), at(2026, 3, 10, 10, 0),
                false, SEOUL, "FREQ=MONTHLY;BYMONTHDAY=10", null, Set.of(), false);

        List<IcsExpander.Occurrence> occurrences = IcsExpander.expand(
                List.of(monthly), at(2026, 3, 1, 0, 0), at(2026, 6, 1, 0, 0));

        assertThat(occurrences).extracting(IcsExpander.Occurrence::startsAt).containsExactly(
                at(2026, 3, 10, 9, 0),
                at(2026, 4, 10, 9, 0),
                at(2026, 5, 10, 9, 0));
    }

    @Test
    @DisplayName("읽지 못하는 규칙이면 첫 회라도 남긴다")
    void keepsFirstOccurrenceForUnknownRule() {
        IcsEvent odd = new IcsEvent("x@example.com", null, "셋째 화요일 회의", null, null,
                at(2026, 3, 17, 9, 0), at(2026, 3, 17, 10, 0),
                false, SEOUL, "FREQ=MONTHLY;BYDAY=3TU;BYSETPOS=3", null, Set.of(), false);

        List<IcsExpander.Occurrence> occurrences = IcsExpander.expand(
                List.of(odd), at(2026, 3, 1, 0, 0), at(2026, 6, 1, 0, 0));

        // 아무것도 안 보이는 것보다는 낫다
        assertThat(occurrences).extracting(IcsExpander.Occurrence::startsAt)
                .containsExactly(at(2026, 3, 17, 9, 0));
    }

    @Test
    @DisplayName("창이 시작되기 전에 시작해 안으로 걸치는 회차도 나온다")
    void includesOverlappingFromBefore() {
        IcsEvent overnight = new IcsEvent("n@example.com", null, "야간", null, null,
                at(2026, 3, 10, 22, 0), at(2026, 3, 11, 6, 0),
                false, SEOUL, null, null, Set.of(), false);

        List<IcsExpander.Occurrence> occurrences = IcsExpander.expand(
                List.of(overnight), at(2026, 3, 11, 0, 0), at(2026, 3, 12, 0, 0));

        assertThat(occurrences).hasSize(1);
    }
}
