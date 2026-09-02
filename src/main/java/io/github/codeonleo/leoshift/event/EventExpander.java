package io.github.codeonleo.leoshift.event;

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
 * 저장된 일정을 달력에 그릴 회차로 펼친다.
 *
 * <p>반복은 DB에서 전개할 수 없으므로 후보만 받아 여기서 편다. 조회 구간이 정해져
 * 있으므로 무한 반복이라도 펼치는 양은 구간 크기로 묶인다.
 *
 * <p>스프링도 JPA도 모른다. 시간대와 반복은 틀리기 쉬운 데 비해 눈으로는 확인이
 * 안 되는 영역이라, 컨테이너 없이 빠르게 돌릴 수 있어야 한다.
 */
public final class EventExpander {

    private EventExpander() {
    }

    public static List<EventInstance> expand(List<EventDefinition> events,
                                             List<OccurrenceException> exceptions,
                                             Instant from,
                                             Instant to) {
        Map<Long, Map<Instant, OccurrenceException>> byEvent = index(exceptions);
        List<EventInstance> instances = new ArrayList<>();

        for (EventDefinition event : events) {
            Map<Instant, OccurrenceException> changes =
                    byEvent.getOrDefault(event.id(), Map.of());

            if (event.isRecurring()) {
                expandSeries(event, changes, from, to, instances);
            } else {
                add(instances, apply(event, event.startsAt(), changes.get(event.startsAt()), false),
                        from, to);
            }
        }

        instances.sort(Comparator
                .comparing(EventInstance::startsAt)
                .thenComparing(EventInstance::endsAt)
                .thenComparing(EventInstance::eventId));
        return instances;
    }

    private static void expandSeries(EventDefinition event,
                                     Map<Instant, OccurrenceException> changes,
                                     Instant from,
                                     Instant to,
                                     List<EventInstance> target) {

        RecurrenceRule rule = RecurrenceRule.parse(event.rrule());
        ZoneId zone = event.zone();
        Duration duration = event.duration();

        // 구간이 시작되기 전에 시작해서 안으로 걸치는 회차도 그려야 한다.
        // 회차 길이만큼 앞으로 넓혀서 편다.
        LocalDateTime windowFrom = LocalDateTime.ofInstant(from.minus(duration), zone);
        LocalDateTime windowTo = LocalDateTime.ofInstant(to, zone);
        LocalDateTime seriesStart = LocalDateTime.ofInstant(event.startsAt(), zone);
        LocalDateTime until = event.recurrenceEnd() == null
                ? null
                : LocalDateTime.ofInstant(event.recurrenceEnd(), zone);

        Set<Instant> emitted = new HashSet<>();

        for (LocalDateTime local : rule.occurrences(seriesStart, windowFrom, windowTo, until)) {
            Instant originalStart = ZonedDateTime.of(local, zone).toInstant();
            emitted.add(originalStart);
            add(target, apply(event, originalStart, changes.get(originalStart), true), from, to);
        }

        // 구간 밖에서 구간 안으로 옮겨온 회차. 원래 자리가 창 밖이라 위 전개에 안 잡힌다.
        // 보강을 다음 주로 옮기면 흔히 이렇게 된다.
        for (OccurrenceException change : changes.values()) {
            if (emitted.contains(change.originalStart()) || change.kind() == OccurrenceException.Kind.CANCELLED) {
                continue;
            }
            add(target, apply(event, change.originalStart(), change, true), from, to);
        }
    }

    /** 회차 하나에 예외를 얹는다. 예외가 없으면 규칙 그대로다. */
    private static EventInstance apply(EventDefinition event,
                                       Instant originalStart,
                                       OccurrenceException change,
                                       boolean recurring) {

        Instant startsAt = originalStart;
        Instant endsAt = originalStart.plus(event.duration());
        String title = event.title();
        EventInstance.Change kind = EventInstance.Change.NONE;

        if (change != null) {
            kind = switch (change.kind()) {
                case CANCELLED -> EventInstance.Change.CANCELLED;
                case MOVED -> EventInstance.Change.MOVED;
                case MODIFIED -> EventInstance.Change.MODIFIED;
            };
            if (change.startsAt() != null) {
                startsAt = change.startsAt();
                // 끝을 따로 적지 않았으면 원래 길이를 유지한다.
                endsAt = change.endsAt() != null
                        ? change.endsAt()
                        : startsAt.plus(event.duration());
            }
            if (change.title() != null && !change.title().isBlank()) {
                title = change.title();
            }
        }

        return new EventInstance(
                event.id(), event.calendarId(), originalStart,
                startsAt, endsAt, event.allDay(), recurring,
                title, event.description(), event.location(), kind);
    }

    private static void add(List<EventInstance> target, EventInstance instance,
                            Instant from, Instant to) {
        if (instance.overlaps(from, to)) {
            target.add(instance);
        }
    }

    private static Map<Long, Map<Instant, OccurrenceException>> index(List<OccurrenceException> exceptions) {
        Map<Long, Map<Instant, OccurrenceException>> byEvent = new HashMap<>();
        for (OccurrenceException change : exceptions) {
            byEvent.computeIfAbsent(change.eventId(), id -> new HashMap<>())
                    .put(change.originalStart(), change);
        }
        return byEvent;
    }
}
