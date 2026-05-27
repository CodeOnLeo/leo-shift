package io.github.codeonleo.leoshift.controller;

import io.github.codeonleo.leoshift.dto.CalendarCreateRequest;
import io.github.codeonleo.leoshift.dto.CalendarGrantPermissionRequest;
import io.github.codeonleo.leoshift.dto.CalendarListResponse;
import io.github.codeonleo.leoshift.dto.CalendarShareInvitationRequest;
import io.github.codeonleo.leoshift.dto.CalendarShareGrantResponse;
import io.github.codeonleo.leoshift.dto.CalendarShareRequest;
import io.github.codeonleo.leoshift.dto.CalendarShareResponse;
import io.github.codeonleo.leoshift.dto.CalendarUpdateRequest;
import io.github.codeonleo.leoshift.dto.CalendarUserGrantRequest;
import io.github.codeonleo.leoshift.dto.ExternalCalendarDisplayRequest;
import io.github.codeonleo.leoshift.dto.ExternalCalendarSourceRequest;
import io.github.codeonleo.leoshift.dto.ExternalCalendarSourceResponse;
import io.github.codeonleo.leoshift.dto.ScheduleTypeResponse;
import io.github.codeonleo.leoshift.dto.ScheduleTypeUpdateRequest;
import io.github.codeonleo.leoshift.dto.ShareDecisionRequest;
import io.github.codeonleo.leoshift.dto.WeeklyRuleResponse;
import io.github.codeonleo.leoshift.dto.WeeklyRuleUpdateRequest;
import io.github.codeonleo.leoshift.service.CalendarAccessService;
import io.github.codeonleo.leoshift.service.CalendarGrantService;
import io.github.codeonleo.leoshift.service.CalendarManagementService;
import io.github.codeonleo.leoshift.service.CalendarShareService;
import io.github.codeonleo.leoshift.service.CalendarWeeklyRuleService;
import io.github.codeonleo.leoshift.service.ExternalCalendarService;
import io.github.codeonleo.leoshift.service.ScheduleTypeService;
import io.github.codeonleo.leoshift.service.SettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final CalendarGrantService calendarGrantService;
    private final CalendarManagementService calendarManagementService;
    private final SettingsService settingsService;
    private final ScheduleTypeService scheduleTypeService;
    private final CalendarWeeklyRuleService calendarWeeklyRuleService;
    private final ExternalCalendarService externalCalendarService;

    public CalendarManagementController(CalendarAccessService calendarAccessService,
                                        CalendarShareService calendarShareService,
                                        CalendarGrantService calendarGrantService,
                                        CalendarManagementService calendarManagementService,
                                        SettingsService settingsService,
                                        ScheduleTypeService scheduleTypeService,
                                        CalendarWeeklyRuleService calendarWeeklyRuleService,
                                        ExternalCalendarService externalCalendarService) {
        this.calendarAccessService = calendarAccessService;
        this.calendarShareService = calendarShareService;
        this.calendarGrantService = calendarGrantService;
        this.calendarManagementService = calendarManagementService;
        this.settingsService = settingsService;
        this.scheduleTypeService = scheduleTypeService;
        this.calendarWeeklyRuleService = calendarWeeklyRuleService;
        this.externalCalendarService = externalCalendarService;
    }

    @GetMapping
    public CalendarListResponse listMyCalendars() {
        var settings = settingsService.getOrCreate();
        Long defaultCalendarId = settings.getDefaultCalendar() != null ? settings.getDefaultCalendar().getId() : null;
        return new CalendarListResponse(calendarAccessService.listAccessible(), defaultCalendarId);
    }

    @PostMapping
    public CalendarListResponse createCalendar(@Valid @RequestBody CalendarCreateRequest request) {
        calendarManagementService.createCalendar(request);
        var settings = settingsService.getOrCreate();
        Long defaultCalendarId = settings.getDefaultCalendar() != null ? settings.getDefaultCalendar().getId() : null;
        return new CalendarListResponse(calendarAccessService.listAccessible(), defaultCalendarId);
    }

    @PutMapping("/{calendarId}")
    public CalendarListResponse updateCalendar(@PathVariable Long calendarId,
                                               @Valid @RequestBody CalendarUpdateRequest request) {
        calendarManagementService.updateCalendar(calendarId, request);
        var settings = settingsService.getOrCreate();
        Long defaultCalendarId = settings.getDefaultCalendar() != null ? settings.getDefaultCalendar().getId() : null;
        return new CalendarListResponse(calendarAccessService.listAccessible(), defaultCalendarId);
    }

    @DeleteMapping("/{calendarId}")
    public CalendarListResponse deleteCalendar(@PathVariable Long calendarId) {
        calendarManagementService.deleteCalendar(calendarId);
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
    public CalendarShareResponse share(@PathVariable Long calendarId, @Valid @RequestBody CalendarShareInvitationRequest request) {
        return calendarShareService.invite(calendarId, new CalendarShareRequest(request.email(), request.permission()));
    }

    @GetMapping("/{calendarId}/shares")
    public List<CalendarShareResponse> shares(@PathVariable Long calendarId) {
        return calendarShareService.listShares(calendarId);
    }

    @GetMapping("/{calendarId}/grants")
    public List<CalendarShareGrantResponse> grants(@PathVariable Long calendarId) {
        return calendarGrantService.listGrants(calendarId);
    }

    @PostMapping("/{calendarId}/grants/users")
    public CalendarShareGrantResponse grantUser(@PathVariable Long calendarId,
                                                @Valid @RequestBody CalendarUserGrantRequest request) {
        return calendarGrantService.grantUser(calendarId, request.email(), request.permission());
    }

    @DeleteMapping("/{calendarId}/grants/users/{userId}")
    public void revokeUser(@PathVariable Long calendarId, @PathVariable Long userId) {
        calendarGrantService.revokeUser(calendarId, userId);
    }

    @PutMapping("/{calendarId}/grants/groups/{groupId}")
    public CalendarShareGrantResponse grantGroup(@PathVariable Long calendarId,
                                                 @PathVariable Long groupId,
                                                 @Valid @RequestBody CalendarGrantPermissionRequest request) {
        return calendarGrantService.grantGroup(calendarId, groupId, request.permission());
    }

    @DeleteMapping("/{calendarId}/grants/groups/{groupId}")
    public void revokeGroup(@PathVariable Long calendarId, @PathVariable Long groupId) {
        calendarGrantService.revokeGroup(calendarId, groupId);
    }

    @PostMapping("/{calendarId}/shares/respond")
    public CalendarShareResponse respond(@PathVariable Long calendarId,
                                         @Valid @RequestBody ShareDecisionRequest request) {
        return calendarShareService.respond(calendarId, request);
    }

    @GetMapping("/{calendarId}/schedule-types")
    public List<ScheduleTypeResponse> scheduleTypes(@PathVariable Long calendarId) {
        var access = calendarAccessService.requireView(calendarId);
        return scheduleTypeService.listForCalendar(access.calendar());
    }

    @PutMapping("/{calendarId}/schedule-types")
    public List<ScheduleTypeResponse> updateScheduleTypes(@PathVariable Long calendarId,
                                                          @Valid @RequestBody ScheduleTypeUpdateRequest request) {
        var access = calendarAccessService.requireEdit(calendarId);
        return scheduleTypeService.updateTypes(access.calendar(), request.scheduleTypes());
    }

    @GetMapping("/{calendarId}/weekly-rules")
    public List<WeeklyRuleResponse> weeklyRules(@PathVariable Long calendarId) {
        var access = calendarAccessService.requireView(calendarId);
        return calendarWeeklyRuleService.list(access.calendar());
    }

    @PutMapping("/{calendarId}/weekly-rules")
    public List<WeeklyRuleResponse> updateWeeklyRules(@PathVariable Long calendarId,
                                                      @Valid @RequestBody WeeklyRuleUpdateRequest request) {
        var access = calendarAccessService.requireEdit(calendarId);
        return calendarWeeklyRuleService.update(access.calendar(), request.rules());
    }

    @GetMapping("/{calendarId}/external-calendars")
    public List<ExternalCalendarSourceResponse> externalCalendars(@PathVariable Long calendarId) {
        var access = calendarAccessService.requireView(calendarId);
        return externalCalendarService.listSources(access.calendar());
    }

    @PostMapping("/{calendarId}/external-calendars")
    public ExternalCalendarSourceResponse addExternalCalendar(@PathVariable Long calendarId,
                                                              @Valid @RequestBody ExternalCalendarSourceRequest request) {
        var access = calendarAccessService.requireEdit(calendarId);
        return externalCalendarService.createSource(access.calendar(), request);
    }

    @PostMapping("/{calendarId}/external-calendars/{sourceId}/sync")
    public ExternalCalendarSourceResponse syncExternalCalendar(@PathVariable Long calendarId,
                                                               @PathVariable Long sourceId) {
        var access = calendarAccessService.requireEdit(calendarId);
        return externalCalendarService.syncSource(access.calendar(), sourceId);
    }

    @PutMapping("/{calendarId}/external-calendars/{sourceId}")
    public ExternalCalendarSourceResponse updateExternalCalendarDisplay(@PathVariable Long calendarId,
                                                                        @PathVariable Long sourceId,
                                                                        @Valid @RequestBody ExternalCalendarDisplayRequest request) {
        var access = calendarAccessService.requireEdit(calendarId);
        return externalCalendarService.updateDisplay(access.calendar(), sourceId, request);
    }

    @DeleteMapping("/{calendarId}/external-calendars/{sourceId}")
    public void deleteExternalCalendar(@PathVariable Long calendarId, @PathVariable Long sourceId) {
        var access = calendarAccessService.requireEdit(calendarId);
        externalCalendarService.deleteSource(access.calendar(), sourceId);
    }
}
