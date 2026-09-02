package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.calendar.CalendarShare;
import io.github.codeonleo.leoshift.domain.event.Event;
import io.github.codeonleo.leoshift.domain.event.EventOccurrence;
import io.github.codeonleo.leoshift.repository.EventOccurrenceRepository;
import io.github.codeonleo.leoshift.repository.EventRepository;
import io.github.codeonleo.leoshift.event.EventDefinition;
import io.github.codeonleo.leoshift.event.EventExpander;
import io.github.codeonleo.leoshift.event.EventInstance;
import io.github.codeonleo.leoshift.event.OccurrenceException;
import io.github.codeonleo.leoshift.event.RecurrenceRule;
import io.github.codeonleo.leoshift.security.CurrentUser;
import io.github.codeonleo.leoshift.service.CalendarAccessService.Access;
import io.github.codeonleo.leoshift.service.CalendarAccessService.NotFoundException;
import io.github.codeonleo.leoshift.web.dto.EventDtos.EventInstanceResponse;
import io.github.codeonleo.leoshift.web.dto.EventDtos.SaveEventRequest;
import io.github.codeonleo.leoshift.web.dto.EventDtos.SaveOccurrenceRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 조회와 편집.
 *
 * <p>반복은 DB에서 펼 수 없으므로 <b>두 갈래로 나눠 가져와</b> 애플리케이션에서 편다.
 * 단발은 기간으로 거르고, 반복은 후보만 받아 RRULE을 전개한다. 전개 자체는
 * {@link EventExpander}가 하고 여기서는 조회와 권한만 맡는다.
 *
 * <p>월 · 주 · 일 화면이 전부 이 하나를 쓴다. 화면마다 다른 경로를 두면 해석이
 * 갈라진다 — 근무 쪽에서 이미 겪은 문제다.
 */
@Service
public class EventService {

    /** 한 번에 펼칠 수 있는 최대 기간. 무한 반복이라도 여기서 묶인다. */
    private static final Duration MAX_RANGE = Duration.ofDays(400);

    private final EventRepository eventRepository;
    private final EventOccurrenceRepository occurrenceRepository;
    private final CalendarAccessService accessService;
    private final CurrentUser currentUser;

    public EventService(EventRepository eventRepository,
                        EventOccurrenceRepository occurrenceRepository,
                        CalendarAccessService accessService,
                        CurrentUser currentUser) {
        this.eventRepository = eventRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.accessService = accessService;
        this.currentUser = currentUser;
    }

    // ---------------------------------------------------------------- 조회

    /**
     * 기간에 걸치는 회차 전부.
     *
     * @param calendarIds 비우면 내가 볼 수 있는 캘린더 전부
     */
    @Transactional(readOnly = true)
    public List<EventInstanceResponse> range(List<Long> calendarIds, Instant from, Instant to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("종료가 시작보다 빠릅니다");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("한 번에 조회할 수 있는 기간을 넘었습니다");
        }

        Map<Long, Access> visible = new LinkedHashMap<>();
        for (Access access : accessService.listVisible()) {
            visible.put(access.calendar().getId(), access);
        }
        // 볼 수 없는 캘린더를 요청하면 조용히 빼지 않고 막는다.
        // 빼버리면 "왜 안 보이지"가 권한 문제인지 데이터가 없는 건지 알 수 없다.
        if (calendarIds != null && !calendarIds.isEmpty()) {
            for (Long calendarId : calendarIds) {
                if (!visible.containsKey(calendarId)) {
                    accessService.requireView(calendarId);
                }
            }
            visible.keySet().retainAll(calendarIds);
        }
        if (visible.isEmpty()) {
            return List.of();
        }

        List<Long> ids = List.copyOf(visible.keySet());
        List<Event> events = new ArrayList<>(eventRepository.findSingleOccurrences(ids, from, to));
        List<Event> recurring = eventRepository.findRecurringCandidates(ids, from, to);
        events.addAll(recurring);

        List<OccurrenceException> exceptions = recurring.isEmpty()
                ? List.of()
                : occurrenceRepository.findByEventIds(recurring.stream().map(Event::getId).toList())
                        .stream().map(EventOccurrence::toDomain).toList();

        List<EventDefinition> definitions = events.stream().map(Event::toDomain).toList();

        return EventExpander.expand(definitions, exceptions, from, to).stream()
                .map(instance -> present(instance, visible.get(instance.calendarId())))
                .toList();
    }

    /**
     * 회차 하나를 화면용으로 바꾼다.
     *
     * <p>{@code 바쁨만} 공유는 여기서 제목과 메모를 지운다. 개인 일정 제목에는
     * "병원", "면담"처럼 남에게 보이면 안 되는 게 들어가는데, 시간이 차 있다는 것만
     * 알리고 싶은 경우가 있다.
     */
    private EventInstanceResponse present(EventInstance instance, Access access) {
        Calendar calendar = access.calendar();
        boolean busyOnly = access.visibility() == CalendarShare.Visibility.BUSY_ONLY;

        return new EventInstanceResponse(
                instance.eventId(), instance.calendarId(), calendar.getName(), calendar.getColor(),
                instance.occurrenceStart(), instance.startsAt(), instance.endsAt(),
                instance.allDay(), instance.recurring(),
                busyOnly ? "바쁨" : instance.title(),
                busyOnly ? null : instance.description(),
                busyOnly ? null : instance.location(),
                instance.change().name(),
                access.canEdit() && !busyOnly);
    }

    /** 편집 화면이 쓰는 원본. 회차가 아니라 시리즈 전체다. */
    @Transactional(readOnly = true)
    public Event get(Long eventId) {
        Event event = eventRepository.findActiveById(eventId)
                .orElseThrow(() -> new NotFoundException("일정을 찾을 수 없습니다"));
        accessService.requireView(event.getCalendar().getId());
        return event;
    }

    // ---------------------------------------------------------------- 편집

    @Transactional
    public Event create(Long calendarId, SaveEventRequest request) {
        Access access = accessService.requireEdit(calendarId);
        validate(request, access.calendar());

        return eventRepository.save(Event.builder()
                .calendar(access.calendar())
                .title(request.title().trim())
                .description(blankToNull(request.description()))
                .location(blankToNull(request.location()))
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .allDay(request.allDay())
                .timeZone(zoneOf(request, access.calendar()))
                .rrule(normalizeRrule(request.rrule()))
                .recurrenceEnd(request.rrule() == null ? null : request.recurrenceEnd())
                .createdBy(currentUser.require())
                .build());
    }

    /**
     * 시리즈 전체를 고친다.
     *
     * <p>시작 시각이나 반복 규칙이 바뀌면 <b>회차 예외를 전부 버린다.</b> 예외는
     * 원래 시각으로 회차를 가리키는데 그 시각이 더 이상 존재하지 않기 때문이다.
     * 남겨두면 "옮겨온 회차" 경로를 타고 엉뚱한 날에 유령 일정이 나타난다.
     */
    @Transactional
    public Event update(Long eventId, SaveEventRequest request) {
        Event event = requireEditable(eventId);
        validate(request, event.getCalendar());

        boolean shifted = !event.getStartsAt().equals(request.startsAt())
                || !java.util.Objects.equals(event.getRrule(), normalizeRrule(request.rrule()));

        event.setTitle(request.title().trim());
        event.setDescription(blankToNull(request.description()));
        event.setLocation(blankToNull(request.location()));
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        event.setAllDay(request.allDay());
        event.setTimeZone(zoneOf(request, event.getCalendar()));
        event.setRrule(normalizeRrule(request.rrule()));
        event.setRecurrenceEnd(request.rrule() == null ? null : request.recurrenceEnd());

        if (shifted) {
            occurrenceRepository.deleteAll(occurrenceRepository.findByEventId(eventId));
        }
        return event;
    }

    /** 시리즈를 지운다. 회차 예외는 캐스케이드로 함께 사라진다. */
    @Transactional
    public void delete(Long eventId) {
        requireEditable(eventId).setDeletedAt(Instant.now());
    }

    /**
     * 반복 중 한 회차만 손댄다. 휴강 · 보강 · 이번 주만 제목 변경이 전부 여기다.
     *
     * <p>예외가 생긴 회차만 저장하므로 반복 전체를 펼쳐 둘 필요가 없다.
     */
    @Transactional
    public void saveOccurrence(Long eventId, SaveOccurrenceRequest request) {
        Event event = requireEditable(eventId);
        if (!event.isRecurring()) {
            throw new IllegalArgumentException("반복 일정이 아닙니다");
        }
        // 실제로 존재하는 회차인지 확인한다. 아니면 어느 화면에도 안 나오는 행이 생기고,
        // 나중에 "옮겨온 회차" 경로를 타고 되살아난다.
        if (!isOccurrence(event, request.originalStart())) {
            throw new IllegalArgumentException("그 시각에는 회차가 없습니다");
        }

        EventOccurrence.Status status;
        Instant startsAt = request.startsAt();
        Instant endsAt = request.endsAt();

        if (request.cancelled()) {
            status = EventOccurrence.Status.CANCELLED;
            startsAt = null;
            endsAt = null;
        } else if (startsAt == null || startsAt.equals(request.originalStart())) {
            status = EventOccurrence.Status.MODIFIED;
            startsAt = request.originalStart();
        } else {
            status = EventOccurrence.Status.MOVED;
        }
        if (endsAt != null && startsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("종료가 시작보다 빠릅니다");
        }

        EventOccurrence occurrence = occurrenceRepository
                .findByEventIdAndOriginalStart(eventId, request.originalStart())
                .orElseGet(() -> EventOccurrence.builder()
                        .event(event)
                        .originalStart(request.originalStart())
                        .status(status)
                        .build());

        occurrence.setStatus(status);
        occurrence.setStartsAt(startsAt);
        occurrence.setEndsAt(endsAt);
        occurrence.setTitle(blankToNull(request.title()));
        occurrence.setNote(blankToNull(request.note()));
        occurrenceRepository.save(occurrence);
    }

    /** 손댄 회차를 규칙대로 되돌린다. 휴강 취소가 여기다. */
    @Transactional
    public void restoreOccurrence(Long eventId, Instant originalStart) {
        requireEditable(eventId);
        occurrenceRepository.findByEventIdAndOriginalStart(eventId, originalStart)
                .ifPresent(occurrenceRepository::delete);
    }

    // ---------------------------------------------------------------- 보조

    private Event requireEditable(Long eventId) {
        Event event = eventRepository.findActiveById(eventId)
                .orElseThrow(() -> new NotFoundException("일정을 찾을 수 없습니다"));
        accessService.requireEdit(event.getCalendar().getId());
        return event;
    }

    /** 그 시각이 이 시리즈의 회차인가. 창을 좁게 잡아 한 번만 확인한다. */
    private boolean isOccurrence(Event event, Instant instant) {
        List<EventInstance> around = EventExpander.expand(
                List.of(event.toDomain()), List.of(),
                instant.minusSeconds(1), instant.plusSeconds(1));

        return around.stream().anyMatch(found -> found.occurrenceStart().equals(instant));
    }

    private static void validate(SaveEventRequest request, Calendar calendar) {
        if (request.endsAt().isBefore(request.startsAt())) {
            throw new IllegalArgumentException("종료가 시작보다 빠릅니다");
        }
        if (request.rrule() != null && !request.rrule().isBlank()) {
            // 파싱해 보고 저장한다. 못 읽는 규칙이 들어가면 그 캘린더의 조회가 통째로 실패한다.
            RecurrenceRule.parse(request.rrule());
            if (request.recurrenceEnd() != null && request.recurrenceEnd().isBefore(request.startsAt())) {
                throw new IllegalArgumentException("반복 종료가 시작보다 빠릅니다");
            }
        }
        zoneOf(request, calendar);
    }

    private static String zoneOf(SaveEventRequest request, Calendar calendar) {
        String zone = request.timeZone() == null || request.timeZone().isBlank()
                ? calendar.getTimeZone()
                : request.timeZone().trim();
        try {
            ZoneId.of(zone);
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("알 수 없는 시간대입니다: " + zone);
        }
        return zone;
    }

    private static String normalizeRrule(String rrule) {
        return rrule == null || rrule.isBlank() ? null : RecurrenceRule.parse(rrule).toRRule();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
