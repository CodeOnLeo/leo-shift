package io.github.codeonleo.leoshift.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EventExpander — 회차 펼치기")
class EventExpanderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), SEOUL).toInstant();
    }

    private static String seoulTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, SEOUL).toString();
    }

    /** 과외 수업. 매주 화 20:30~21:30. 설계 문서 4.5의 기준 사례다. */
    private static EventDefinition lesson() {
        return new EventDefinition(
                1L, 10L, "김과외 수업", null, "자택",
                at(2026, 3, 3, 20, 30), at(2026, 3, 3, 21, 30),
                false, SEOUL, "FREQ=WEEKLY;BYDAY=TU", null);
    }

    @Test
    @DisplayName("정각이 아닌 시각도 그대로 반복된다")
    void keepsOffGridTimes() {
        List<EventInstance> found = EventExpander.expand(
                List.of(lesson()), List.of(),
                at(2026, 3, 1, 0, 0), at(2026, 3, 22, 0, 0));

        assertThat(found).extracting(i -> seoulTime(i.startsAt())).containsExactly(
                "2026-03-03T20:30", "2026-03-10T20:30", "2026-03-17T20:30");
        assertThat(seoulTime(found.get(0).endsAt())).isEqualTo("2026-03-03T21:30");
        assertThat(found).allSatisfy(i -> assertThat(i.recurring()).isTrue());
    }

    @Test
    @DisplayName("휴강은 지우지 않고 취소된 회차로 남는다")
    void cancelledStaysVisible() {
        OccurrenceException off = new OccurrenceException(
                1L, at(2026, 3, 10, 20, 30), OccurrenceException.Kind.CANCELLED,
                null, null, null, "휴강");

        List<EventInstance> found = EventExpander.expand(
                List.of(lesson()), List.of(off),
                at(2026, 3, 1, 0, 0), at(2026, 3, 22, 0, 0));

        // 달력에서 사라지면 "이번 주 수업 있었나?"를 확인할 수 없다.
        assertThat(found).hasSize(3);
        assertThat(found.get(1).isCancelled()).isTrue();
        assertThat(found.get(0).isCancelled()).isFalse();
    }

    @Test
    @DisplayName("보강으로 옮긴 회차는 옮긴 자리에 나온다")
    void movedOccurrence() {
        OccurrenceException makeup = new OccurrenceException(
                1L, at(2026, 3, 10, 20, 30), OccurrenceException.Kind.MOVED,
                at(2026, 3, 12, 19, 0), at(2026, 3, 12, 20, 0), null, null);

        List<EventInstance> found = EventExpander.expand(
                List.of(lesson()), List.of(makeup),
                at(2026, 3, 8, 0, 0), at(2026, 3, 15, 0, 0));

        assertThat(found).extracting(i -> seoulTime(i.startsAt()))
                .containsExactly("2026-03-12T19:00");
        assertThat(found.get(0).change()).isEqualTo(EventInstance.Change.MOVED);
        // 원래 회차를 가리키는 열쇠는 그대로여야 되돌릴 수 있다.
        assertThat(seoulTime(found.get(0).occurrenceStart())).isEqualTo("2026-03-10T20:30");
    }

    @Test
    @DisplayName("구간 밖에서 안으로 옮겨온 회차도 나온다")
    void movedIntoWindowFromOutside() {
        // 3/10 수업을 다음 주로 미뤘다. 원래 자리는 조회 구간 밖이다.
        OccurrenceException makeup = new OccurrenceException(
                1L, at(2026, 3, 10, 20, 30), OccurrenceException.Kind.MOVED,
                at(2026, 3, 18, 19, 0), null, null, null);

        List<EventInstance> found = EventExpander.expand(
                List.of(lesson()), List.of(makeup),
                at(2026, 3, 16, 0, 0), at(2026, 3, 23, 0, 0));

        assertThat(found).extracting(i -> seoulTime(i.startsAt()))
                .containsExactly("2026-03-17T20:30", "2026-03-18T19:00");
        // 끝을 따로 적지 않았으면 원래 길이(1시간)를 유지한다.
        assertThat(seoulTime(found.get(1).endsAt())).isEqualTo("2026-03-18T20:00");
    }

    @Test
    @DisplayName("구간 시작 전에 시작해 안으로 걸치는 회차도 그린다")
    void spansIntoWindow() {
        EventDefinition overnight = new EventDefinition(
                2L, 10L, "야간 당직", null, null,
                at(2026, 3, 3, 22, 0), at(2026, 3, 4, 6, 0),
                false, SEOUL, "FREQ=DAILY", null);

        // 3/4 00:00부터 조회하면 3/3 22:00에 시작한 회차가 아직 진행 중이다.
        List<EventInstance> found = EventExpander.expand(
                List.of(overnight), List.of(),
                at(2026, 3, 4, 0, 0), at(2026, 3, 4, 12, 0));

        assertThat(found).extracting(i -> seoulTime(i.startsAt()))
                .containsExactly("2026-03-03T22:00");
    }

    @Test
    @DisplayName("서머타임을 넘어도 벽시계 시각이 유지된다")
    void survivesDaylightSaving() {
        ZoneId newYork = ZoneId.of("America/New_York");
        // 2026-03-08에 서머타임이 시작된다. 절대 시각에 일주일을 더하면 20:30이 19:30이 된다.
        Instant start = ZonedDateTime.of(LocalDateTime.of(2026, 3, 3, 20, 30), newYork).toInstant();

        EventDefinition lesson = new EventDefinition(
                3L, 10L, "화상 수업", null, null,
                start, start.plusSeconds(3600),
                false, newYork, "FREQ=WEEKLY;BYDAY=TU", null);

        List<EventInstance> found = EventExpander.expand(
                List.of(lesson), List.of(),
                start.minusSeconds(86400), start.plusSeconds(86400L * 21));

        assertThat(found).extracting(i -> LocalDateTime.ofInstant(i.startsAt(), newYork).toLocalTime().toString())
                .containsOnly("20:30");
    }

    @Test
    @DisplayName("단발 일정은 겹칠 때만 나온다")
    void singleEvent() {
        EventDefinition once = new EventDefinition(
                4L, 10L, "병원", null, null,
                at(2026, 3, 5, 14, 0), at(2026, 3, 5, 15, 0),
                false, SEOUL, null, null);

        assertThat(EventExpander.expand(List.of(once), List.of(),
                at(2026, 3, 5, 0, 0), at(2026, 3, 6, 0, 0))).hasSize(1);

        assertThat(EventExpander.expand(List.of(once), List.of(),
                at(2026, 3, 6, 0, 0), at(2026, 3, 7, 0, 0))).isEmpty();
    }

    @Test
    @DisplayName("반복 종료일 이후에는 나오지 않는다")
    void respectsRecurrenceEnd() {
        EventDefinition ending = new EventDefinition(
                5L, 10L, "스터디", null, null,
                at(2026, 3, 3, 20, 30), at(2026, 3, 3, 21, 30),
                false, SEOUL, "FREQ=WEEKLY;BYDAY=TU", at(2026, 3, 10, 23, 59));

        List<EventInstance> found = EventExpander.expand(
                List.of(ending), List.of(),
                at(2026, 3, 1, 0, 0), at(2026, 4, 1, 0, 0));

        assertThat(found).extracting(i -> seoulTime(i.startsAt()))
                .containsExactly("2026-03-03T20:30", "2026-03-10T20:30");
    }

    @Test
    @DisplayName("한 회차만 제목이 달라진다")
    void modifiedTitle() {
        OccurrenceException renamed = new OccurrenceException(
                1L, at(2026, 3, 10, 20, 30), OccurrenceException.Kind.MODIFIED,
                at(2026, 3, 10, 20, 30), null, "김과외 시험 대비", null);

        List<EventInstance> found = EventExpander.expand(
                List.of(lesson()), List.of(renamed),
                at(2026, 3, 9, 0, 0), at(2026, 3, 12, 0, 0));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).title()).isEqualTo("김과외 시험 대비");
        assertThat(found.get(0).change()).isEqualTo(EventInstance.Change.MODIFIED);
    }
}
