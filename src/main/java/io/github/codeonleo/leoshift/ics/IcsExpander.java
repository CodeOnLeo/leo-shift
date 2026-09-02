package io.github.codeonleo.leoshift.ics;

import io.github.codeonleo.leoshift.event.RecurrenceRule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 구독한 피드의 VEVENT를 창 안의 회차로 펼친다.
 *
 * <p>구독한 일정은 우리 {@code events}가 아니라 {@code external_events} 캐시에
 * 회차 단위로 저장한다. 반복을 규칙으로 들고 있어 봐야 <b>남의 규칙</b>이라 우리가
 * 못 읽는 형태가 섞이고, 조회할 때마다 실패할 수 있다. 동기화할 때 한 번 펼쳐
 * 넣으면 조회는 단순한 기간 질의가 된다.
 *
 * <p>이전 구현은 반복 일정의 <b>첫 회만</b> 보여줬다. 매주 회의를 구독하면 첫 주만
 * 달력에 뜨고 그 뒤로는 아무것도 없었다.
 *
 * <p>스프링도 JPA도 모른다.
 */
public final class IcsExpander {

    /** 한 피드에서 만들어낼 최대 회차 수. 창이 있어도 일정 수 × 회차 수는 커질 수 있다. */
    private static final int MAX_OCCURRENCES = 50_000;

    private IcsExpander() {
    }

    /**
     * 달력에 그려질 회차 하나.
     *
     * <p>{@code uid}는 원본 VEVENT의 것을 그대로 쓴다. 저장 유니크가
     * {@code (source, uid, starts_at)}이라, 회차마다 다른 행이 되면서도 다시
     * 동기화했을 때 같은 회차는 같은 행으로 맞춰진다.
     */
    public record Occurrence(
            String uid,
            String summary,
            String description,
            String location,
            Instant startsAt,
            Instant endsAt,
            boolean allDay
    ) {
    }

    public static List<Occurrence> expand(List<IcsEvent> events, Instant from, Instant to) {
        // 한 회차만 고친 줄(RECURRENCE-ID)은 그 자체로 하나고,
        // 부모 시리즈는 그 회차를 건너뛰어야 한다. 안 그러면 옮기기 전과 후가 둘 다 뜬다.
        Map<String, Set<Instant>> replaced = new HashMap<>();
        for (IcsEvent event : events) {
            if (event.isOverride()) {
                replaced.computeIfAbsent(event.uid(), uid -> new HashSet<>()).add(event.recurrenceId());
            }
        }

        List<Occurrence> occurrences = new ArrayList<>();
        for (IcsEvent event : events) {
            if (occurrences.size() >= MAX_OCCURRENCES) {
                break;
            }
            if (event.isOverride() || !event.isRecurring()) {
                if (overlaps(event.startsAt(), event.endsAt(), from, to)) {
                    occurrences.add(toOccurrence(event, event.startsAt(), event.endsAt()));
                }
                continue;
            }
            expandSeries(event, replaced.getOrDefault(event.uid(), Set.of()), from, to, occurrences);
        }

        occurrences.sort(Comparator.comparing(Occurrence::startsAt).thenComparing(Occurrence::uid));
        return List.copyOf(occurrences);
    }

    private static void expandSeries(IcsEvent event, Set<Instant> replaced,
                                     Instant from, Instant to, List<Occurrence> target) {

        RecurrenceRule rule = readRule(event);
        if (rule == null) {
            // 우리가 못 읽는 규칙이다. 첫 회라도 보여주는 게 아무것도 안 보이는 것보다 낫다.
            if (overlaps(event.startsAt(), event.endsAt(), from, to)) {
                target.add(toOccurrence(event, event.startsAt(), event.endsAt()));
            }
            return;
        }

        ZoneId zone = event.zone();
        Duration length = Duration.between(event.startsAt(), event.endsAt());

        // 창이 시작되기 전에 시작해서 안으로 걸치는 회차도 그려야 한다.
        LocalDateTime windowFrom = LocalDateTime.ofInstant(from.minus(length), zone);
        LocalDateTime windowTo = LocalDateTime.ofInstant(to, zone);
        LocalDateTime seriesStart = LocalDateTime.ofInstant(event.startsAt(), zone);
        LocalDateTime until = event.until() == null ? null : LocalDateTime.ofInstant(event.until(), zone);

        for (LocalDateTime local : rule.occurrences(seriesStart, windowFrom, windowTo, until)) {
            Instant startsAt = ZonedDateTime.of(local, zone).toInstant();
            if (event.exDates().contains(startsAt) || replaced.contains(startsAt)) {
                continue;
            }
            Instant endsAt = startsAt.plus(length);
            if (overlaps(startsAt, endsAt, from, to)) {
                target.add(toOccurrence(event, startsAt, endsAt));
            }
            if (target.size() >= MAX_OCCURRENCES) {
                return;
            }
        }
    }

    /**
     * 남의 RRULE을 우리 규칙으로 읽는다.
     *
     * <p>우리 파서는 매일 · 매주 · 매월 · 매년과 간격 · 횟수만 안다. 외부 피드에는
     * 그 밖의 것이 섞여 오는데, <b>DTSTART로 이미 정해지는 값을 다시 적은 것</b>은
     * 떼어내도 뜻이 바뀌지 않는다. 구글이 매월 · 매년 반복에 붙여 보내는
     * {@code BYMONTHDAY} · {@code BYMONTH}가 대부분 여기 해당한다.
     *
     * @return 읽지 못하면 null
     */
    private static RecurrenceRule readRule(IcsEvent event) {
        LocalDateTime start = LocalDateTime.ofInstant(event.startsAt(), event.zone());
        StringBuilder kept = new StringBuilder();

        for (String part : event.rrule().split(";")) {
            int equals = part.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = part.substring(0, equals).trim().toUpperCase();
            String value = part.substring(equals + 1).trim();

            boolean redundant = switch (key) {
                case "BYMONTHDAY" -> value.equals(String.valueOf(start.getDayOfMonth()));
                case "BYMONTH" -> value.equals(String.valueOf(start.getMonthValue()));
                // UNTIL은 event.until()이 이미 들고 있고, WKST는 우리 전개에 영향이 없다
                case "UNTIL", "WKST" -> true;
                default -> false;
            };
            if (!redundant) {
                if (kept.length() > 0) {
                    kept.append(';');
                }
                kept.append(key).append('=').append(value);
            }
        }

        try {
            return RecurrenceRule.parse(kept.toString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Occurrence toOccurrence(IcsEvent event, Instant startsAt, Instant endsAt) {
        return new Occurrence(event.uid(), event.summary(), event.description(), event.location(),
                startsAt, endsAt, event.allDay());
    }

    /** 끝이 창 시작과 같은 일정은 걸치지 않은 것으로 본다. */
    private static boolean overlaps(Instant startsAt, Instant endsAt, Instant from, Instant to) {
        return endsAt.isAfter(from) && startsAt.isBefore(to);
    }
}
