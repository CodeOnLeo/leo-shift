package io.github.codeonleo.leoshift.ics;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * iCalendar(RFC 5545) 본문에서 VEVENT를 읽는다.
 *
 * <p><b>관대하게 읽는다.</b> 남의 서버가 만든 문서라 우리가 못 읽는 속성이 반드시
 * 섞여 있다. 모르는 속성은 건너뛰고, 한 줄이 깨져도 그 VEVENT만 버린다. 문서 하나가
 * 통째로 실패하면 사용자는 "구독이 안 된다"는 것밖에 알 수 없다.
 *
 * <p>스프링도 JPA도 모른다.
 */
public final class IcsParser {

    /** 한 문서에서 읽어들일 최대 VEVENT 수. 악의적이거나 망가진 피드를 막는다. */
    private static final int MAX_EVENTS = 20_000;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private IcsParser() {
    }

    /**
     * @param fallbackZone TZID가 없거나 우리가 모르는 이름일 때 쓸 시간대.
     *                     보통 구독하는 캘린더의 시간대를 넘긴다
     */
    public static List<IcsEvent> parse(String text, ZoneId fallbackZone) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        ZoneId zone = fallbackZone == null ? ZoneOffset.UTC : fallbackZone;

        List<IcsEvent> events = new ArrayList<>();
        List<String> block = null;

        for (String line : unfold(text)) {
            String upper = line.toUpperCase();
            if (upper.startsWith("BEGIN:VEVENT")) {
                block = new ArrayList<>();
            } else if (upper.startsWith("END:VEVENT")) {
                if (block != null) {
                    // 한 건이 깨져도 나머지는 살린다. 남의 문서다.
                    IcsEvent event = toEvent(block, zone);
                    if (event != null && !event.cancelled()) {
                        events.add(event);
                    }
                }
                block = null;
                if (events.size() >= MAX_EVENTS) {
                    return List.copyOf(events);
                }
            } else if (block != null) {
                block.add(line);
            }
        }
        return List.copyOf(events);
    }

    // ---------------------------------------------------------------- 줄 단위

    /**
     * 접힌 줄을 편다.
     *
     * <p>RFC 5545는 75옥텟마다 줄을 꺾고 다음 줄을 공백이나 탭으로 시작한다.
     * 펴지 않고 읽으면 긴 제목이 통째로 잘린다.
     */
    private static List<String> unfold(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String raw : text.split("\r\n|\r|\n", -1)) {
            if (!raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t')) {
                current.append(raw, 1, raw.length());
                continue;
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
            current.setLength(0);
            current.append(raw);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    /** {@code DTSTART;TZID=Asia/Seoul:20260310T203000} 을 이름 · 파라미터 · 값으로. */
    private static Property property(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ':' && !quoted) {
                return parseName(line.substring(0, i), line.substring(i + 1));
            }
        }
        return null;
    }

    private static Property parseName(String head, String value) {
        String[] parts = splitParams(head);
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            if (equals > 0) {
                params.put(parts[i].substring(0, equals).trim().toUpperCase(),
                        unquote(parts[i].substring(equals + 1).trim()));
            }
        }
        return new Property(parts[0].trim().toUpperCase(), params, value);
    }

    /** 따옴표 안의 세미콜론은 구분자가 아니다. */
    private static String[] splitParams(String head) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                current.append(c);
            } else if (c == ';' && !quoted) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts.toArray(String[]::new);
    }

    private static String unquote(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1)
                : value;
    }

    private record Property(String name, Map<String, String> params, String value) {

        ZoneId zone(ZoneId fallback) {
            String tzid = params.get("TZID");
            if (tzid == null || tzid.isBlank()) {
                return fallback;
            }
            try {
                return ZoneId.of(tzid);
            } catch (DateTimeException e) {
                // 윈도우 계열 이름("W. Europe Standard Time")은 IANA가 아니다.
                // 시각을 버리느니 캘린더 시간대로 읽는다.
                return fallback;
            }
        }

        boolean isDateOnly() {
            return "DATE".equalsIgnoreCase(params.get("VALUE")) || value.trim().length() == 8;
        }
    }

    // ---------------------------------------------------------------- VEVENT

    /** @return 읽지 못하면 null. 그 한 건만 버린다 */
    private static IcsEvent toEvent(List<String> block, ZoneId fallbackZone) {
        String uid = null;
        String summary = null;
        String description = null;
        String location = null;
        String rrule = null;
        Instant recurrenceId = null;
        Instant startsAt = null;
        Instant endsAt = null;
        Duration duration = null;
        boolean allDay = false;
        boolean cancelled = false;
        // DTSTART가 적혀 있던 시간대. 반복을 벽시계 기준으로 펴려면 이게 있어야 한다.
        ZoneId zone = fallbackZone;
        Set<Instant> exDates = new LinkedHashSet<>();

        try {
            for (String line : block) {
                Property property = property(line);
                if (property == null) {
                    continue;
                }
                switch (property.name()) {
                    case "UID" -> uid = unescape(property.value()).trim();
                    case "SUMMARY" -> summary = unescape(property.value());
                    case "DESCRIPTION" -> description = unescape(property.value());
                    case "LOCATION" -> location = unescape(property.value());
                    case "RRULE" -> rrule = property.value().trim();
                    case "STATUS" -> cancelled = "CANCELLED".equalsIgnoreCase(property.value().trim());
                    case "DTSTART" -> {
                        allDay = property.isDateOnly();
                        zone = property.zone(fallbackZone);
                        startsAt = instant(property.value().trim(), zone, allDay, false);
                    }
                    case "DTEND" -> endsAt = instant(property.value().trim(), property.zone(fallbackZone),
                            property.isDateOnly(), false);
                    case "DURATION" -> duration = duration(property.value().trim());
                    case "RECURRENCE-ID" -> recurrenceId = instant(property.value().trim(),
                            property.zone(fallbackZone), property.isDateOnly(), false);
                    case "EXDATE" -> {
                        ZoneId exZone = property.zone(fallbackZone);
                        for (String value : property.value().split(",")) {
                            if (!value.isBlank()) {
                                exDates.add(instant(value.trim(), exZone, value.trim().length() == 8, false));
                            }
                        }
                    }
                    default -> { /* 모르는 속성은 건너뛴다 */ }
                }
            }

            if (uid == null || uid.isBlank() || startsAt == null) {
                return null;
            }
            if (endsAt == null) {
                // DTEND가 없으면 DURATION, 그것도 없으면 종일은 하루 · 시각은 길이 0이다 (RFC 5545 3.6.1)
                endsAt = duration != null
                        ? startsAt.plus(duration)
                        : (allDay ? startsAt.plus(Duration.ofDays(1)) : startsAt);
            }
            if (endsAt.isBefore(startsAt)) {
                endsAt = startsAt;
            }

            return new IcsEvent(uid, recurrenceId, blankToNull(summary), blankToNull(description),
                    blankToNull(location), startsAt, endsAt, allDay, zone,
                    blankToNull(rrule), until(rrule, zone), exDates, cancelled);

        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * RRULE에서 UNTIL을 뽑는다.
     *
     * <p><b>여기서 뽑지 않으면 끝난 반복이 영원히 도는 것으로 읽힌다.</b> 반복 규칙
     * 파서는 UNTIL을 일부러 무시하는데(종료는 컬럼이 담당한다), 외부 피드는 그 컬럼
     * 대신 문자열 안에 종료를 넣어 보낸다.
     */
    private static Instant until(String rrule, ZoneId fallbackZone) {
        if (rrule == null) {
            return null;
        }
        for (String part : rrule.split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0 && "UNTIL".equalsIgnoreCase(part.substring(0, equals).trim())) {
                String value = part.substring(equals + 1).trim();
                try {
                    return instant(value, fallbackZone, value.length() == 8, true);
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 값

    /**
     * @param endOfDay 날짜만 주어졌을 때 그날 끝으로 볼 것인가. UNTIL이 그렇다
     */
    private static Instant instant(String value, ZoneId zone, boolean dateOnly, boolean endOfDay) {
        if (dateOnly) {
            LocalDate date = LocalDate.parse(value, DATE);
            return (endOfDay ? date.plusDays(1).atStartOfDay() : date.atStartOfDay())
                    .atZone(zone).toInstant();
        }
        if (value.endsWith("Z")) {
            return LocalDateTime.parse(value.substring(0, value.length() - 1), DATE_TIME)
                    .toInstant(ZoneOffset.UTC);
        }
        return LocalDateTime.parse(value, DATE_TIME).atZone(zone).toInstant();
    }

    /** {@code P1DT2H30M}. 주(W)는 {@link Duration#parse}가 못 읽어서 먼저 바꾼다. */
    private static Duration duration(String value) {
        String text = value.toUpperCase();
        if (text.matches("[+-]?P\\d+W")) {
            int weeks = Integer.parseInt(text.replaceAll("[^0-9]", ""));
            Duration weekly = Duration.ofDays(7L * weeks);
            return text.startsWith("-") ? weekly.negated() : weekly;
        }
        return Duration.parse(text);
    }

    /** TEXT 값의 이스케이프를 푼다. 제목에 쉼표가 들어가면 여기가 없을 때 잘린다. */
    private static String unescape(String value) {
        StringBuilder text = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                text.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n', 'N' -> text.append('\n');
                case '\\', ';', ',' -> text.append(next);
                default -> text.append('\\').append(next);
            }
        }
        return text.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
