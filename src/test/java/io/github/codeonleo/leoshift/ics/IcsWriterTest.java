package io.github.codeonleo.leoshift.ics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 내보내는 .ics가 남의 앱에서 읽히는 모양인지 본다. */
class IcsWriterTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static IcsWriter writer() {
        return IcsWriter.calendar("내 근무", SEOUL, Duration.ofHours(6));
    }

    @Test
    @DisplayName("시각 있는 일정을 UTC로 적는다")
    void writesTimedEvent() {
        String ics = writer()
                .timed("work-1-2026-03-10@leo-shift",
                        Instant.parse("2026-03-09T21:00:00Z"),
                        Instant.parse("2026-03-10T05:00:00Z"),
                        "주간", null, null)
                .finish();

        assertThat(ics).contains("BEGIN:VEVENT")
                .contains("UID:work-1-2026-03-10@leo-shift")
                .contains("DTSTART:20260309T210000Z")
                .contains("DTEND:20260310T050000Z")
                .contains("SUMMARY:주간")
                .contains("END:VCALENDAR");
    }

    @Test
    @DisplayName("종일 일정의 끝은 다음 날이다")
    void writesAllDayWithExclusiveEnd() {
        String ics = writer()
                .allDay("x@leo-shift", LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11), "휴무", null)
                .finish();

        // 같은 날을 적으면 길이 0이 되어 어떤 클라이언트에서는 사라진다
        assertThat(ics).contains("DTSTART;VALUE=DATE:20260310")
                .contains("DTEND;VALUE=DATE:20260311");
    }

    @Test
    @DisplayName("모든 줄이 CRLF로 끝난다")
    void usesCrlf() {
        String ics = writer().finish();
        assertThat(ics.split("\r\n")).isNotEmpty();
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    @DisplayName("쉼표와 세미콜론을 이스케이프한다")
    void escapesText() {
        String ics = writer()
                .timed("x@leo-shift", Instant.EPOCH, Instant.EPOCH,
                        "회의, 3층; 본관", null, null)
                .finish();

        // 이스케이프가 없으면 읽는 쪽이 쉼표에서 값을 자른다
        assertThat(ics).contains("SUMMARY:회의\\, 3층\\; 본관");
    }

    @Test
    @DisplayName("긴 줄을 75옥텟에서 접되 글자를 쪼개지 않는다")
    void foldsWithoutSplittingCharacters() {
        String longKorean = "가".repeat(80);
        String folded = IcsWriter.fold("SUMMARY:" + longKorean);

        for (String line : folded.split("\r\n")) {
            // 이어지는 줄의 앞 공백까지 합쳐 75옥텟을 넘지 않아야 한다
            int octets = line.getBytes(StandardCharsets.UTF_8).length;
            assertThat(octets).isLessThanOrEqualTo(75);
        }
        // 접힌 것을 도로 펴면 원래 글자가 그대로여야 한다 — 깨진 글자가 없다는 뜻이다
        String unfolded = Arrays.stream(folded.split("\r\n"))
                .map(line -> line.startsWith(" ") ? line.substring(1) : line)
                .reduce("", String::concat);
        assertThat(unfolded).isEqualTo("SUMMARY:" + longKorean);
    }

    @Test
    @DisplayName("제목이 비면 자리를 채운다")
    void fillsMissingSummary() {
        String ics = writer().timed("x@leo-shift", Instant.EPOCH, Instant.EPOCH, null, null, null).finish();
        assertThat(ics).contains("SUMMARY:(제목 없음)");
    }
}
