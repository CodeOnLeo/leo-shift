package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.dto.CalendarListResponse;
import io.github.codeonleo.leoshift.dto.CalendarShareRequest;
import io.github.codeonleo.leoshift.dto.CalendarShareResponse;
import io.github.codeonleo.leoshift.dto.ShareDecisionRequest;
import io.github.codeonleo.leoshift.service.CalendarAccessService;
import io.github.codeonleo.leoshift.service.CalendarShareService;
import io.github.codeonleo.leoshift.service.SettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendars")
public class CalendarManagementController {

    private final CalendarAccessService calendarAccessService;
    private final CalendarShareService calendarShareService;
    private final SettingsService settingsService;

    public CalendarManagementController(CalendarAccessService calendarAccessService,
                                        CalendarShareService calendarShareService,
                                        SettingsService settingsService) {
        this.calendarAccessService = calendarAccessService;
        this.calendarShareService = calendarShareService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public CalendarListResponse listMyCalendars() {
        var settings = settingsService.getOrCreate();
        Long defaultCalendarId = settings.getDefaultCalendar() != null ? settings.getDefaultCalendar().getId() : null;
        return new CalendarListResponse(calendarAccessService.listAccessible(), defaultCalendarId);
    }

    @PutMapping("/{calendarId}/default")
    public CalendarListResponse setDefault(@PathVariable Long calendarId) {
        var access = calendarAccessService.requireView(calendarId);
        settingsService.setDefaultCalendar(access.calendar());
        return new CalendarListResponse(calendarAccessService.listAccessible(), access.calendar().getId());
    }

    @PostMapping("/{calendarId}/share")
    public CalendarShareResponse share(@PathVariable Long calendarId, @Valid @RequestBody CalendarShareRequest request) {
        return calendarShareService.invite(calendarId, request);
    }

    @GetMapping("/{calendarId}/shares")
    public List<CalendarShareResponse> shares(@PathVariable Long calendarId) {
        return calendarShareService.listShares(calendarId);
    }

    @PostMapping("/{calendarId}/shares/respond")
    public CalendarShareResponse respond(@PathVariable Long calendarId,
                                         @Valid @RequestBody ShareDecisionRequest request) {
        return calendarShareService.respond(calendarId, request);
    }
}
