package io.github.codeonleo.leoshift.web.dto;

import io.github.codeonleo.leoshift.domain.work.WorkRule;
import io.github.codeonleo.leoshift.schedule.preset.AnchorHint;
import io.github.codeonleo.leoshift.schedule.preset.PatternPreset;
import io.github.codeonleo.leoshift.schedule.preset.ScheduleTypeSpec;
import io.github.codeonleo.leoshift.schedule.preset.TeamOption;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class PatternDtos {

    private PatternDtos() {
    }

    public record PresetTypeResponse(
            String code, String name, String color, String category,
            LocalTime startTime, LocalTime endTime, boolean crossesMidnight) {

        static PresetTypeResponse from(ScheduleTypeSpec spec) {
            return new PresetTypeResponse(spec.code(), spec.name(), spec.color(),
                    spec.category().name(), spec.startTime(), spec.endTime(), spec.crossesMidnight());
        }
    }

    public record TeamResponse(String label, int offset) {
        static TeamResponse from(TeamOption team) {
            return new TeamResponse(team.label(), team.offset());
        }
    }

    /**
     * @param anchorQuestion 기준일을 묻는 문구. "패턴의 1일차"를 묻는 대신
     *                       사용자가 아는 사실을 물어 역산한다
     */
    public record PresetResponse(
            String id, String name, String category, List<String> tags, String description,
            String anchorWeekday, int cycleLength, List<String> sequence,
            List<PresetTypeResponse> scheduleTypes, List<TeamResponse> teams,
            String anchorCode, String anchorQuestion) {

        public static PresetResponse from(PatternPreset preset, List<ScheduleTypeSpec> allTypes) {
            AnchorHint hint = preset.anchorHint();
            return new PresetResponse(
                    preset.id(),
                    preset.name(),
                    preset.category().name(),
                    preset.tags(),
                    preset.description(),
                    preset.anchorWeekday() == null ? null : preset.anchorWeekday().name(),
                    preset.cycleLength(),
                    preset.sequence(),
                    allTypes.stream().map(PresetTypeResponse::from).toList(),
                    preset.teams().stream().map(TeamResponse::from).toList(),
                    hint == null ? null : hint.code(),
                    hint == null ? null : hint.question());
        }
    }

    public record WorkRuleResponse(
            Long id, LocalDate anchorDate, int cycleLength, List<String> sequence,
            LocalDate effectiveFrom, LocalDate effectiveTo, String sourcePresetId) {

        public static WorkRuleResponse from(WorkRule rule) {
            return new WorkRuleResponse(
                    rule.getId(), rule.getAnchorDate(), rule.getCycleLength(), rule.getCodeSequence(),
                    rule.getEffectiveFrom(), rule.getEffectiveTo(), rule.getSourcePresetId());
        }
    }

    /**
     * 근무 패턴 적용 요청.
     *
     * <p>{@code presetId}만 주면 프리셋의 시퀀스를 쓰고, {@code sequence}를 함께 주면
     * 사용자가 편집한 시퀀스를 쓴다. 어느 쪽이든 필요한 일정 타입이 없으면 만든다.
     *
     * @param effectiveFrom 이 날부터 새 패턴이 적용된다. 이전 규칙은 그 전날까지로
     *                      닫히고 과거 근무표는 그대로 남는다
     */
    public record ApplyPatternRequest(
            String presetId,
            String teamLabel,
            @Size(max = 366, message = "주기가 너무 깁니다") List<String> sequence,
            @NotNull(message = "기준일을 골라 주세요") LocalDate anchorDate,
            @NotNull(message = "적용 시작일을 골라 주세요") LocalDate effectiveFrom) {
    }
}
