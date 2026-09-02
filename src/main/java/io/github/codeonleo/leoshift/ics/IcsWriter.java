package io.github.codeonleo.leoshift.ics;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * iCalendar(RFC 5545) 문서를 만든다.
 *
 * <p>내 근무표를 구글 캘린더에서 볼 수 있게 하는 쪽이다. 근무표는 이 앱에서 만들고
 * 보기는 각자 익숙한 앱에서 한다.
 *
 * <p><b>반복은 RRULE로 내보내지 않고 회차를 펼쳐서 내보낸다.</b> TZID를 쓰려면
 * VTIMEZONE 블록을 함께 실어야 하고, 그러지 않고 UTC로 적으면 서머타임이 있는
 * 시간대에서 "매주 화 20:30"이 한 시간씩 밀린다. 창이 정해져 있어 양도 묶이고,
 * 휴강 · 보강 같은 회차 예외가 저절로 반영된다.
 *
 * <p>스프링도 JPA도 모른다.
 */
public final class IcsWriter {

    /** 한 줄의 최대 길이. RFC 5545는 옥텟 기준 75자로 접으라고 한다. */
    private static final int FOLD_OCTETS = 75;

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringBuilder out = new StringBuilder();
    private final Instant stamp = Instant.now();

    private IcsWriter() {
    }

    /**
     * @param refresh 구독하는 쪽에 권하는 갱신 주기. 구글은 어차피 제 주기로 읽지만,
     *                적어 두면 대부분의 클라이언트가 참고한다
     */
    public static IcsWriter calendar(String name, ZoneId zone, Duration refresh) {
        IcsWriter writer = new IcsWriter();
        writer.line("BEGIN:VCALENDAR");
        writer.line("VERSION:2.0");
        writer.line("PRODID:-//Leo Shift//Calendar Feed//KO");
        writer.line("CALSCALE:GREGORIAN");
        writer.line("METHOD:PUBLISH");
        writer.line("X-WR-CALNAME:" + escape(name));
        writer.line("X-WR-TIMEZONE:" + zone.getId());
        String ttl = iso(refresh);
        writer.line("REFRESH-INTERVAL;VALUE=DURATION:" + ttl);
        writer.line("X-PUBLISHED-TTL:" + ttl);
        return writer;
    }

    /** 시각이 있는 일정. 근무 코드에 출퇴근 시간이 있는 경우와 개인 일정이 여기다. */
    public IcsWriter timed(String uid, Instant startsAt, Instant endsAt,
                           String summary, String description, String location) {
        begin(uid, summary, description, location);
        line("DTSTART:" + UTC.format(startsAt));
        line("DTEND:" + UTC.format(endsAt));
        line("END:VEVENT");
        return this;
    }

    /**
     * 종일 일정. 휴무 · 휴가처럼 시각이 없는 근무가 여기다.
     *
     * @param endExclusive 마지막 날의 <b>다음</b> 날. RFC 5545의 DTEND는 열린 끝이라
     *                     같은 날을 적으면 길이 0이 되고 어떤 클라이언트에서는 사라진다
     */
    public IcsWriter allDay(String uid, LocalDate startsOn, LocalDate endExclusive,
                            String summary, String description) {
        begin(uid, summary, description, null);
        line("DTSTART;VALUE=DATE:" + DATE.format(startsOn));
        line("DTEND;VALUE=DATE:" + DATE.format(endExclusive));
        line("TRANSP:TRANSPARENT");
        line("END:VEVENT");
        return this;
    }

    public String finish() {
        line("END:VCALENDAR");
        return out.toString();
    }

    // ---------------------------------------------------------------- 내부

    private void begin(String uid, String summary, String description, String location) {
        line("BEGIN:VEVENT");
        line("UID:" + escape(uid));
        line("DTSTAMP:" + UTC.format(stamp));
        line("SUMMARY:" + escape(summary == null || summary.isBlank() ? "(제목 없음)" : summary));
        if (description != null && !description.isBlank()) {
            line("DESCRIPTION:" + escape(description));
        }
        if (location != null && !location.isBlank()) {
            line("LOCATION:" + escape(location));
        }
    }

    private void line(String content) {
        out.append(fold(content)).append("\r\n");
    }

    /**
     * 긴 줄을 접는다.
     *
     * <p>옥텟으로 세되 문자 가운데를 자르지 않는다. 한글은 UTF-8에서 3바이트라
     * 문자 수로 세면 넘치고, 바이트로 잘라 버리면 깨진 글자가 나간다.
     */
    static String fold(String content) {
        if (content.getBytes(StandardCharsets.UTF_8).length <= FOLD_OCTETS) {
            return content;
        }
        StringBuilder folded = new StringBuilder(content.length() + 16);
        int octets = 0;

        for (int i = 0; i < content.length(); ) {
            int codePoint = content.codePointAt(i);
            int width = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;

            // 이어지는 줄은 공백 하나로 시작하고, 그 공백도 한 옥텟을 차지한다
            if (octets + width > FOLD_OCTETS) {
                folded.append("\r\n ");
                octets = 1;
            }
            folded.appendCodePoint(codePoint);
            octets += width;
            i += Character.charCount(codePoint);
        }
        return folded.toString();
    }

    /** TEXT 값의 이스케이프. 제목에 쉼표가 있으면 이게 없을 때 뒤가 잘린다. */
    static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n")
                .replace(";", "\\;")
                .replace(",", "\\,");
    }

    /** {@code PT6H} 형태. Duration.toString()이 이미 ISO-8601이지만 소수 초가 붙을 수 있다. */
    private static String iso(Duration refresh) {
        long minutes = Math.max(1, refresh.toMinutes());
        return minutes % 60 == 0 ? "PT" + (minutes / 60) + "H" : "PT" + minutes + "M";
    }
}
