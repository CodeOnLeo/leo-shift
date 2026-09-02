package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.service.CalendarService;
import io.github.codeonleo.leoshift.web.dto.CalendarDtos.MyCalendarResponse;
import io.github.codeonleo.leoshift.web.dto.CalendarDtos.SaveCalendarRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 캘린더 관리.
 *
 * <p>{@code /api/calendars}(볼 수 있는 캘린더 전부)와 달리 여기는 <b>내가 소유한</b>
 * 것만 다룬다. 공유받은 캘린더는 이름을 바꾸거나 지울 수 없다.
 */
@RestController
@RequestMapping("/api/my/calendars")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    public List<MyCalendarResponse> list() {
        return calendarService.list();
    }

    @PostMapping
    public MyCalendarResponse create(@Valid @RequestBody SaveCalendarRequest request) {
        return calendarService.create(request);
    }

    @PutMapping("/{calendarId}")
    public MyCalendarResponse update(@PathVariable Long calendarId,
                                     @Valid @RequestBody SaveCalendarRequest request) {
        return calendarService.update(calendarId, request);
    }

    /** 일정을 만들 때 미리 골라져 있는 캘린더. */
    @PutMapping("/{calendarId}/default")
    public ResponseEntity<Void> setDefault(@PathVariable Long calendarId) {
        calendarService.setDefault(calendarId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{calendarId}")
    public ResponseEntity<Void> delete(@PathVariable Long calendarId) {
        calendarService.delete(calendarId);
        return ResponseEntity.noContent().build();
    }
}
