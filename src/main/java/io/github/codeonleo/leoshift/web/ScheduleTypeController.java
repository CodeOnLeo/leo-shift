package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.service.CalendarAccessService;
import io.github.codeonleo.leoshift.service.ScheduleTypeService;
import io.github.codeonleo.leoshift.web.dto.ScheduleDtos.ScheduleTypeResponse;
import io.github.codeonleo.leoshift.web.dto.ScheduleTypeDtos.SaveScheduleTypeRequest;
import io.github.codeonleo.leoshift.web.dto.ScheduleTypeDtos.ScheduleTypeUsageResponse;
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

@RestController
@RequestMapping("/api/calendars/{calendarId}/schedule-types")
public class ScheduleTypeController {

    private final ScheduleTypeService scheduleTypeService;
    private final CalendarAccessService accessService;

    public ScheduleTypeController(ScheduleTypeService scheduleTypeService,
                                  CalendarAccessService accessService) {
        this.scheduleTypeService = scheduleTypeService;
        this.accessService = accessService;
    }

    @GetMapping
    public List<ScheduleTypeResponse> list(@PathVariable Long calendarId) {
        accessService.requireView(calendarId);
        return scheduleTypeService.list(calendarId).stream()
                .map(ScheduleTypeResponse::from)
                .toList();
    }

    /** 이 코드를 지울 수 있는지. 화면에서 삭제 버튼을 흐리게 하거나 이유를 보여주는 데 쓴다. */
    @GetMapping("/{code}/usage")
    public ScheduleTypeUsageResponse usage(@PathVariable Long calendarId, @PathVariable String code) {
        accessService.requireView(calendarId);
        return scheduleTypeService.usage(calendarId, code.toUpperCase());
    }

    @PostMapping
    public ScheduleTypeResponse create(@PathVariable Long calendarId,
                                       @Valid @RequestBody SaveScheduleTypeRequest request) {
        CalendarAccessService.Access access = accessService.requireEdit(calendarId);
        return ScheduleTypeResponse.from(scheduleTypeService.create(access.calendar(), request));
    }

    /** 코드 이름을 바꾸면 반복 근무·휴가·날짜별 변경의 참조가 함께 따라간다. */
    @PutMapping("/{code}")
    public ScheduleTypeResponse update(@PathVariable Long calendarId,
                                       @PathVariable String code,
                                       @Valid @RequestBody SaveScheduleTypeRequest request) {
        CalendarAccessService.Access access = accessService.requireEdit(calendarId);
        return ScheduleTypeResponse.from(scheduleTypeService.update(access.calendar(), code, request));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable Long calendarId, @PathVariable String code) {
        CalendarAccessService.Access access = accessService.requireEdit(calendarId);
        scheduleTypeService.delete(access.calendar(), code);
        return ResponseEntity.noContent().build();
    }
}
