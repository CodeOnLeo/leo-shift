package io.github.codeonleo.leoshift.web;

import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.schedule.preset.PatternPreset;
import io.github.codeonleo.leoshift.service.CalendarAccessService;
import io.github.codeonleo.leoshift.service.WorkPatternService;
import io.github.codeonleo.leoshift.web.dto.PatternDtos.ApplyPatternRequest;
import io.github.codeonleo.leoshift.web.dto.PatternDtos.PresetResponse;
import io.github.codeonleo.leoshift.web.dto.PatternDtos.WorkRuleResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PatternController {

    private final WorkPatternService patternService;
    private final CalendarAccessService accessService;

    public PatternController(WorkPatternService patternService, CalendarAccessService accessService) {
        this.patternService = patternService;
        this.accessService = accessService;
    }

    /** 근무 패턴 프리셋 목록. 앱에 동봉된 리소스라 DB 조회가 없다. */
    @GetMapping("/presets")
    public List<PresetResponse> presets() {
        return patternService.presets().all().stream()
                .map(this::toResponse)
                .toList();
    }

    private PresetResponse toResponse(PatternPreset preset) {
        return PresetResponse.from(preset, patternService.presets().scheduleTypesFor(preset));
    }

    @GetMapping("/calendars/{calendarId}/work-rule")
    public ResponseEntity<WorkRuleResponse> current(@PathVariable Long calendarId) {
        accessService.requireView(calendarId);
        return patternService.current(calendarId)
                .map(WorkRuleResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * 근무 패턴을 적용한다.
     *
     * <p>기존 패턴은 지워지지 않는다. 새 패턴 시작 전날까지로 닫히므로
     * 지난 근무표를 그대로 볼 수 있다.
     */
    @PutMapping("/calendars/{calendarId}/work-rule")
    public WorkRuleResponse apply(@PathVariable Long calendarId,
                                  @Valid @RequestBody ApplyPatternRequest request) {
        CalendarAccessService.Access access = accessService.requireEdit(calendarId);
        WorkRule saved = patternService.apply(access.calendar(), request);
        return WorkRuleResponse.from(saved);
    }
}
