package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.service.EventService;
import io.github.codeonleo.leoshift.web.dto.EventDtos.EventRangeResponse;
import io.github.codeonleo.leoshift.web.dto.EventDtos.EventResponse;
import io.github.codeonleo.leoshift.web.dto.EventDtos.SaveEventRequest;
import io.github.codeonleo.leoshift.web.dto.EventDtos.SaveOccurrenceRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * 기간에 걸치는 회차 전부. 월 · 주 · 일 화면이 전부 이 하나를 쓴다.
     *
     * @param calendarId 여러 번 줄 수 있다. 비우면 볼 수 있는 캘린더 전부
     */
    @GetMapping("/events")
    public EventRangeResponse range(@RequestParam Instant from,
                                    @RequestParam Instant to,
                                    @RequestParam(required = false) List<Long> calendarId) {
        return new EventRangeResponse(from, to, eventService.range(calendarId, from, to));
    }

    /**
     * 시리즈 원본. 편집 화면이 반복 규칙까지 그대로 받아야 한다.
     *
     * <p>회차 정보만으로 폼을 채우면 "반복 전체"로 저장할 때 규칙이 비어 나가고,
     * 매주 수업이 조용히 단발 일정이 된다.
     */
    @GetMapping("/events/{eventId}")
    public EventResponse get(@PathVariable Long eventId) {
        return EventResponse.from(eventService.get(eventId));
    }

    @PostMapping("/calendars/{calendarId}/events")
    public EventResponse create(@PathVariable Long calendarId,
                                @Valid @RequestBody SaveEventRequest request) {
        return EventResponse.from(eventService.create(calendarId, request));
    }

    @PutMapping("/events/{eventId}")
    public EventResponse update(@PathVariable Long eventId,
                                @Valid @RequestBody SaveEventRequest request) {
        return EventResponse.from(eventService.update(eventId, request));
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> delete(@PathVariable Long eventId) {
        eventService.delete(eventId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 반복 중 한 회차만. 휴강 · 보강 · 이번 주만 제목 변경.
     *
     * <p>회차 시각을 경로가 아니라 본문에 두는 이유는 ISO 시각의 {@code +09:00}이
     * 경로에서 공백으로 해석되기 때문이다.
     */
    @PutMapping("/events/{eventId}/occurrences")
    public ResponseEntity<Void> saveOccurrence(@PathVariable Long eventId,
                                               @Valid @RequestBody SaveOccurrenceRequest request) {
        eventService.saveOccurrence(eventId, request);
        return ResponseEntity.noContent().build();
    }

    /** 손댄 회차를 규칙대로 되돌린다. */
    @DeleteMapping("/events/{eventId}/occurrences")
    public ResponseEntity<Void> restoreOccurrence(@PathVariable Long eventId,
                                                  @RequestParam Instant originalStart) {
        eventService.restoreOccurrence(eventId, originalStart);
        return ResponseEntity.noContent().build();
    }
}
