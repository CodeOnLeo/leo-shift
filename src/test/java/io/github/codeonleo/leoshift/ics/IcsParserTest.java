package io.github.codeonleo.leoshift.ics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ICS 읽기.
 *
 * <p>여기서 확인하는 건 대부분 <b>이전 구현이 틀렸던 것</b>이다 — 시각이 사라지고,
 * 반복이 첫 회만 나오고, 접힌 줄이 잘렸다.
 */
class IcsParserTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static String ics(String... vevent) {
        StringBuilder text = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n");
        for (String line : vevent) {
            text.append(line).append("\r\n");
        }
        return text.append("END:VCALENDAR\r\n").toString();
    }

    @Nested
    @DisplayName("시각")
    class Times {

        @Test
        @DisplayName("TZID가 붙은 현지 시각을 그대로 읽는다")
        void readsZonedLocalTime() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "SUMMARY:수업",
                    "DTSTART;TZID=Asia/Seoul:20260310T203000",
                    "DTEND;TZID=Asia/Seoul:20260310T213000",
                    "END:VEVENT"), SEOUL);

            assertThat(events).hasSize(1);
            IcsEvent event = events.get(0);
            assertThat(event.startsAt())
                    .isEqualTo(LocalDate.of(2026, 3, 10).atTime(20, 30).atZone(SEOUL).toInstant());
            assertThat(event.allDay()).isFalse();
            assertThat(event.zone()).isEqualTo(SEOUL);
        }

        @Test
        @DisplayName("UTC 표기(Z)를 읽는다")
        void readsUtc() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "DTSTART:20260310T113000Z",
                    "DTEND:20260310T123000Z",
                    "END:VEVENT"), SEOUL);

            assertThat(events.get(0).startsAt()).isEqualTo(Instant.parse("2026-03-10T11:30:00Z"));
        }

        @Test
        @DisplayName("종일 일정의 끝은 열린 끝이다")
        void readsAllDay() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "DTSTART;VALUE=DATE:20260310",
                    "DTEND;VALUE=DATE:20260312",
                    "END:VEVENT"), SEOUL);

            IcsEvent event = events.get(0);
            assertThat(event.allDay()).isTrue();
            assertThat(event.startsAt()).isEqualTo(LocalDate.of(2026, 3, 10).atStartOfDay(SEOUL).toInstant());
            assertThat(event.endsAt()).isEqualTo(LocalDate.of(2026, 3, 12).atStartOfDay(SEOUL).toInstant());
        }

        @Test
        @DisplayName("DTEND가 없으면 DURATION으로 끝을 잡는다")
        void readsDuration() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "DTSTART:20260310T113000Z",
                    "DURATION:PT90M",
                    "END:VEVENT"), SEOUL);

            assertThat(events.get(0).endsAt()).isEqualTo(Instant.parse("2026-03-10T13:00:00Z"));
        }

        @Test
        @DisplayName("모르는 시간대 이름은 캘린더 시간대로 읽는다")
        void fallsBackForUnknownZone() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "DTSTART;TZID=W. Europe Standard Time:20260310T203000",
                    "DTEND;TZID=W. Europe Standard Time:20260310T213000",
                    "END:VEVENT"), SEOUL);

            // 시각을 버리느니 캘린더 시간대로 읽는다. 통째로 실패하는 것보다 낫다.
            assertThat(events).hasSize(1);
            assertThat(events.get(0).zone()).isEqualTo(SEOUL);
        }
    }

    @Nested
    @DisplayName("본문")
    class Text {

        @Test
        @DisplayName("접힌 줄을 편다")
        void unfolds() {
            String folded = "BEGIN:VCALENDAR\r\n"
                    + "BEGIN:VEVENT\r\n"
                    + "UID:a@example.com\r\n"
                    + "SUMMARY:아주 긴 제목이라서 여기서 한 번 꺾이고\r\n"
                    + "  이어서 붙는다\r\n"
                    + "DTSTART:20260310T113000Z\r\n"
                    + "DTEND:20260310T123000Z\r\n"
                    + "END:VEVENT\r\n"
                    + "END:VCALENDAR\r\n";

            List<IcsEvent> events = IcsParser.parse(folded, SEOUL);
            assertThat(events.get(0).summary()).isEqualTo("아주 긴 제목이라서 여기서 한 번 꺾이고 이어서 붙는다");
        }

        @Test
        @DisplayName("이스케이프된 쉼표·줄바꿈을 되돌린다")
        void unescapes() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "SUMMARY:회의\\, 3층",
                    "DESCRIPTION:첫 줄\\n둘째 줄",
                    "DTSTART:20260310T113000Z",
                    "DTEND:20260310T123000Z",
                    "END:VEVENT"), SEOUL);

            assertThat(events.get(0).summary()).isEqualTo("회의, 3층");
            assertThat(events.get(0).description()).isEqualTo("첫 줄\n둘째 줄");
        }

        @Test
        @DisplayName("취소된 일정은 버린다")
        void dropsCancelled() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "STATUS:CANCELLED",
                    "DTSTART:20260310T113000Z",
                    "DTEND:20260310T123000Z",
                    "END:VEVENT"), SEOUL);

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("한 건이 깨져도 나머지는 읽는다")
        void survivesBrokenEvent() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:broken@example.com",
                    "DTSTART:이건 시각이 아니다",
                    "END:VEVENT",
                    "BEGIN:VEVENT",
                    "UID:fine@example.com",
                    "DTSTART:20260310T113000Z",
                    "DTEND:20260310T123000Z",
                    "END:VEVENT"), SEOUL);

            assertThat(events).extracting(IcsEvent::uid).containsExactly("fine@example.com");
        }
    }

    @Nested
    @DisplayName("반복")
    class Recurrence {

        @Test
        @DisplayName("UNTIL을 규칙 문자열이 아니라 종료 시각으로 뽑는다")
        void extractsUntil() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "DTSTART;TZID=Asia/Seoul:20260310T203000",
                    "DTEND;TZID=Asia/Seoul:20260310T213000",
                    "RRULE:FREQ=WEEKLY;BYDAY=TU;UNTIL=20260501T000000Z",
                    "END:VEVENT"), SEOUL);

            // 여기서 뽑지 않으면 끝난 반복이 영원히 도는 것으로 읽힌다
            assertThat(events.get(0).until()).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"));
        }

        @Test
        @DisplayName("EXDATE를 모은다")
        void collectsExDates() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "DTSTART;TZID=Asia/Seoul:20260310T203000",
                    "DTEND;TZID=Asia/Seoul:20260310T213000",
                    "RRULE:FREQ=WEEKLY;BYDAY=TU",
                    "EXDATE;TZID=Asia/Seoul:20260317T203000,20260324T203000",
                    "END:VEVENT"), SEOUL);

            assertThat(events.get(0).exDates()).containsExactlyInAnyOrder(
                    LocalDate.of(2026, 3, 17).atTime(20, 30).atZone(SEOUL).toInstant(),
                    LocalDate.of(2026, 3, 24).atTime(20, 30).atZone(SEOUL).toInstant());
        }

        @Test
        @DisplayName("RECURRENCE-ID가 붙은 줄은 한 회차만 고친 것이다")
        void readsOverride() {
            List<IcsEvent> events = IcsParser.parse(ics(
                    "BEGIN:VEVENT",
                    "UID:a@example.com",
                    "RECURRENCE-ID;TZID=Asia/Seoul:20260317T203000",
                    "DTSTART;TZID=Asia/Seoul:20260318T203000",
                    "DTEND;TZID=Asia/Seoul:20260318T213000",
                    "END:VEVENT"), SEOUL);

            assertThat(events.get(0).isOverride()).isTrue();
            assertThat(events.get(0).recurrenceId())
                    .isEqualTo(LocalDate.of(2026, 3, 17).atTime(20, 30).atZone(SEOUL).toInstant());
        }
    }
}
