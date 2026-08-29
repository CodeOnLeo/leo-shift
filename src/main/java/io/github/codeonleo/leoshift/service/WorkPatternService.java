package io.github.codeonleo.leoshift.service;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.repository.ScheduleTypeRepository;
import io.github.codeonleo.leoshift.repository.WorkRuleRepository;
import io.github.codeonleo.leoshift.schedule.preset.PatternPreset;
import io.github.codeonleo.leoshift.schedule.preset.PatternPresets;
import io.github.codeonleo.leoshift.schedule.preset.ScheduleTypeSpec;
import io.github.codeonleo.leoshift.web.dto.PatternDtos.ApplyPatternRequest;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 근무 패턴 적용.
 *
 * <p>패턴을 바꿔도 <b>과거 근무표는 그대로 남는다.</b> 기존 규칙을 고치는 게 아니라
 * 새 규칙의 시작 전날까지로 닫고 새 규칙을 잇는다. 이전 구현은 패턴을 바꾸면
 * 기존 패턴을 통째로 삭제해서 지난 근무를 재현할 수 없었다.
 */
@Service
public class WorkPatternService {

    private final WorkRuleRepository workRuleRepository;
    private final ScheduleTypeRepository scheduleTypeRepository;
    private final PatternPresets presets = PatternPresets.load();

    public WorkPatternService(WorkRuleRepository workRuleRepository,
                              ScheduleTypeRepository scheduleTypeRepository) {
        this.workRuleRepository = workRuleRepository;
        this.scheduleTypeRepository = scheduleTypeRepository;
    }

    public PatternPresets presets() {
        return presets;
    }

    @Transactional(readOnly = true)
    public Optional<WorkRule> current(Long calendarId) {
        return workRuleRepository.findLatest(calendarId);
    }

    @Transactional
    public WorkRule apply(Calendar calendar, ApplyPatternRequest request) {
        PatternPreset preset = request.presetId() == null
                ? null
                : presets.byId(request.presetId())
                        .orElseThrow(() -> new IllegalArgumentException("없는 프리셋입니다"));

        List<String> sequence = resolveSequence(preset, request);
        if (sequence.isEmpty()) {
            throw new IllegalArgumentException("근무 순서를 하나 이상 지정해야 합니다");
        }
        if (sequence.size() > 366) {
            throw new IllegalArgumentException("주기가 너무 깁니다");
        }

        // 프리셋을 골랐으면 필요한 일정 타입을 먼저 만든다.
        // 사용자가 코드를 하나씩 만든 뒤 패턴을 짜야 했던 순서를 뒤집는다.
        if (preset != null) {
            ensureScheduleTypes(calendar, presets.scheduleTypesFor(preset));
        }
        validateCodesExist(calendar, sequence);

        LocalDate anchorDate = preset != null ? preset.snapAnchor(request.anchorDate()) : request.anchorDate();
        LocalDate effectiveFrom = request.effectiveFrom();

        closePreviousRules(calendar.getId(), effectiveFrom);

        return workRuleRepository.save(WorkRule.builder()
                .calendar(calendar)
                .anchorDate(anchorDate)
                .cycleLength(sequence.size())
                .codeSequence(sequence)
                .effectiveFrom(effectiveFrom)
                .sourcePresetId(preset == null ? null : preset.id())
                .build());
    }

    private List<String> resolveSequence(PatternPreset preset, ApplyPatternRequest request) {
        if (request.sequence() != null && !request.sequence().isEmpty()) {
            return request.sequence().stream()
                    .map(code -> code == null ? "" : code.trim().toUpperCase())
                    .filter(code -> !code.isEmpty())
                    .toList();
        }
        if (preset == null) {
            throw new IllegalArgumentException("프리셋이나 근무 순서 중 하나는 있어야 합니다");
        }
        return preset.sequenceFor(request.teamLabel());
    }

    /** 없는 타입만 만든다. 이미 있는 것은 사용자가 고쳤을 수 있으므로 건드리지 않는다. */
    private void ensureScheduleTypes(Calendar calendar, List<ScheduleTypeSpec> specs) {
        Set<String> existing = new HashSet<>();
        scheduleTypeRepository.findByCalendar(calendar.getId())
                .forEach(type -> existing.add(type.getCode()));

        int order = 10 + existing.size() * 10;
        for (ScheduleTypeSpec spec : specs) {
            if (existing.contains(spec.code())) {
                continue;
            }
            scheduleTypeRepository.save(ScheduleType.builder()
                    .calendar(calendar)
                    .code(spec.code())
                    .name(spec.name())
                    .color(spec.color())
                    .category(ScheduleType.Category.valueOf(spec.category().name()))
                    .startTime(spec.startTime())
                    .endTime(spec.endTime())
                    .crossesMidnight(spec.crossesMidnight())
                    .halfDay(spec.halfDay())
                    .sortOrder(order)
                    .build());
            order += 10;
        }
    }

    private void validateCodesExist(Calendar calendar, List<String> sequence) {
        Set<String> known = new HashSet<>();
        scheduleTypeRepository.findByCalendar(calendar.getId())
                .forEach(type -> known.add(type.getCode()));

        for (String code : sequence) {
            if (!known.contains(code)) {
                throw new IllegalArgumentException("등록되지 않은 근무 코드입니다: " + code);
            }
        }
    }

    /**
     * 새 패턴 시작 전날까지로 기존 규칙을 닫는다.
     *
     * <p>DB의 배제 제약이 기간 겹침을 막으므로, 닫지 않고 새 규칙을 넣으면 저장이 실패한다.
     * 시작일 이후에 시작하는 규칙은 새 패턴에 밀려나므로 지운다.
     */
    private void closePreviousRules(Long calendarId, LocalDate effectiveFrom) {
        for (WorkRule rule : workRuleRepository.findAllByCalendar(calendarId)) {
            if (!rule.getEffectiveFrom().isBefore(effectiveFrom)) {
                workRuleRepository.delete(rule);
                continue;
            }
            if (rule.getEffectiveTo() == null || !rule.getEffectiveTo().isBefore(effectiveFrom)) {
                rule.setEffectiveTo(effectiveFrom.minusDays(1));
                workRuleRepository.save(rule);
            }
        }
        workRuleRepository.flush();
    }
}
