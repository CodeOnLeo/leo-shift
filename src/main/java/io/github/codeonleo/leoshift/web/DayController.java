package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.service.CalendarAccessService;
import io.github.codeonleo.leoshift.service.DayDetailService;
import io.github.codeonleo.leoshift.web.dto.DayDtos.DayDetailResponse;
import io.github.codeonleo.leoshift.web.dto.DayDtos.SaveLeaveRequest;
import io.github.codeonleo.leoshift.web.dto.DayDtos.SaveOverrideRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendars/{calendarId}")
public class DayController {

    private final DayDetailService dayDetailService;
    private final CalendarAccessService accessService;

    public DayController(DayDetailService dayDetailService, CalendarAccessService accessService) {
        this.dayDetailService = dayDetailService;
        this.accessService = accessService;
    }

    @GetMapping("/days/{date}")
    public DayDetailResponse day(@PathVariable Long calendarId,
                                 @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return dayDetailService.load(accessService.requireView(calendarId), date);
    }

    /** 날짜별 예외를 저장한다. 코드도 메모도 없으면 예외를 지우고 규칙으로 되돌린다. */
    @PutMapping("/days/{date}")
    public DayDetailResponse saveDay(@PathVariable Long calendarId,
                                     @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @Valid @RequestBody SaveOverrideRequest request) {
        CalendarAccessService.Access access = accessService.requireEdit(calendarId);
        dayDetailService.saveOverride(access.calendar(), date, request);
        return dayDetailService.load(access, date);
    }

    @DeleteMapping("/days/{date}")
    public DayDetailResponse clearDay(@PathVariable Long calendarId,
                                      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        CalendarAccessService.Access access = accessService.requireEdit(calendarId);
        dayDetailService.clearOverride(access.calendar(), date);
        return dayDetailService.load(access, date);
    }

    @PostMapping("/leaves")
    public ResponseEntity<Void> addLeave(@PathVariable Long calendarId,
                                         @Valid @RequestBody SaveLeaveRequest request) {
        CalendarAccessService.Access access = accessService.requireEdit(calendarId);
        dayDetailService.saveLeave(access.calendar(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/leaves/{leaveId}")
    public ResponseEntity<Void> deleteLeave(@PathVariable Long calendarId, @PathVariable Long leaveId) {
        CalendarAccessService.Access access = accessService.requireEdit(calendarId);
        dayDetailService.deleteLeave(access.calendar(), leaveId);
        return ResponseEntity.noContent().build();
    }
}
