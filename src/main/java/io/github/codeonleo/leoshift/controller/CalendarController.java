package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.dto.CalendarResponse;
import io.github.codeonleo.leoshift.dto.DayDetailResponse;
import io.github.codeonleo.leoshift.dto.ExceptionUpdateRequest;
import io.github.codeonleo.leoshift.dto.TodayResponse;
import io.github.codeonleo.leoshift.service.CalendarAccessService;
import io.github.codeonleo.leoshift.service.CalendarService;
import io.github.codeonleo.leoshift.service.DayDetailService;
import io.github.codeonleo.leoshift.service.TodayService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CalendarController {

    private final CalendarService calendarService;
    private final TodayService todayService;
    private final DayDetailService dayDetailService;
    private final CalendarAccessService calendarAccessService;

    public CalendarController(CalendarService calendarService, TodayService todayService, DayDetailService dayDetailService, CalendarAccessService calendarAccessService) {
        this.calendarService = calendarService;
        this.todayService = todayService;
        this.dayDetailService = dayDetailService;
        this.calendarAccessService = calendarAccessService;
    }

    @GetMapping("/calendar")
    public CalendarResponse getCalendar(@RequestParam(required = false) Integer year,
                                        @RequestParam(required = false) Integer month,
                                        @RequestParam(required = false) Long calendarId) {
        CalendarAccessService.CalendarAccess access = calendarAccessService.requireView(calendarId);
        LocalDate now = LocalDate.now(ZoneId.systemDefault());
        int targetYear = year != null ? year : now.getYear();
        int targetMonth = month != null ? month : now.getMonthValue();
        return calendarService.buildMonthlyCalendar(access.calendar(), targetYear, targetMonth);
    }

    @GetMapping("/today")
    public TodayResponse today(@RequestParam(required = false) Long calendarId) {
        return todayService.buildTodayView(calendarId);
    }

    @GetMapping("/days/{date}")
    public DayDetailResponse getDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @RequestParam(required = false) Long calendarId) {
        CalendarAccessService.CalendarAccess access = calendarAccessService.requireView(calendarId);
        return dayDetailService.load(date, access.calendar());
    }

    @PutMapping("/days/{date}")
    public DayDetailResponse updateDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @Valid @RequestBody ExceptionUpdateRequest request,
                                       @RequestParam(required = false) Long calendarId) {
        CalendarAccessService.CalendarAccess access = calendarAccessService.requireEdit(calendarId);
        return dayDetailService.save(date, request, access.calendar());
    }
}
