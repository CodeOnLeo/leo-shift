package io.github.codeonleo.leoshift.schedule.preset;

import io.github.codeonleo.leoshift.schedule.WorkRule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 근무 패턴 프리셋 하나.
 *
 * <p><b>프리셋은 출발점이지 정답이 아니다.</b> 사용자가 고르는 순간 시퀀스가
 * 사용자 캘린더로 복사되고, 이후 이 정의가 바뀌어도 기존 사용자에게 영향이 없다.
 * {@code work_rules.source_preset_id}는 참조가 아니라 출처 기록이다.
 *
 * @param anchorWeekday REGULAR 전용. 기준일이 이 요일이어야 요일과 어긋나지 않는다.
 *                      SHIFT는 사용자가 기준일을 정하므로 null이다.
 * @param teams         교대조 목록. 조 개념이 없는 프리셋은 비어 있다.
 * @param anchorHint    기준일을 묻는 방법. 없으면 null.
 */
public record PatternPreset(
        String id,
        String name,
        Category category,
        List<String> tags,
        String description,
        DayOfWeek anchorWeekday,
        List<String> sequence,
        List<ScheduleTypeSpec> scheduleTypes,
        List<TeamOption> teams,
        AnchorHint anchorHint
) {

    public enum Category {
        /** 요일 기반 일반 근무. 요일 그리드 UI로 편집한다. */
        REGULAR,
        /** 순환 교대 근무. 순서 빌더 UI로 편집한다. */
        SHIFT
    }

    public PatternPreset {
        Objects.requireNonNull(category, "category는 필수다");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id는 필수다");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name은 필수다: " + id);
        }
        if (sequence == null || sequence.isEmpty()) {
            throw new IllegalArgumentException("sequence는 필수다: " + id);
        }
        if (scheduleTypes == null || scheduleTypes.isEmpty()) {
            throw new IllegalArgumentException("scheduleTypes는 필수다: " + id);
        }

        tags = tags == null ? List.of() : List.copyOf(tags);
        sequence = List.copyOf(sequence);
        scheduleTypes = List.copyOf(scheduleTypes);
        teams = teams == null ? List.of() : List.copyOf(teams);

        Set<String> definedCodes = new LinkedHashSet<>();
        for (ScheduleTypeSpec spec : scheduleTypes) {
            if (!definedCodes.add(spec.code())) {
                throw new IllegalArgumentException("일정 타입 코드가 중복이다: " + id + " / " + spec.code());
            }
        }
        for (String code : sequence) {
            if (!definedCodes.contains(code)) {
                throw new IllegalArgumentException(
                        "시퀀스가 정의되지 않은 코드를 쓴다: " + id + " / " + code + " (정의됨: " + definedCodes + ")");
            }
        }

        Set<String> labels = new LinkedHashSet<>();
        for (TeamOption team : teams) {
            if (!labels.add(team.label())) {
                throw new IllegalArgumentException("조 이름이 중복이다: " + id + " / " + team.label());
            }
            if (team.offset() >= sequence.size()) {
                throw new IllegalArgumentException(
                        "조의 offset이 주기를 넘는다: " + id + " / " + team.label()
                                + " offset=" + team.offset() + " 주기=" + sequence.size());
            }
        }

        if (anchorHint != null && !sequence.contains(anchorHint.code())) {
            throw new IllegalArgumentException(
                    "anchorHint가 시퀀스에 없는 코드를 가리킨다: " + id + " / " + anchorHint.code());
        }

        // 요일 규칙은 주기가 7의 배수여야 요일과 정렬된다
        if (category == Category.REGULAR) {
            if (anchorWeekday == null) {
                throw new IllegalArgumentException("REGULAR 프리셋은 anchorWeekday가 필요하다: " + id);
            }
            if (sequence.size() % 7 != 0) {
                throw new IllegalArgumentException(
                        "REGULAR 프리셋의 주기는 7의 배수여야 한다: " + id + " / " + sequence.size());
            }
        }
    }

    public int cycleLength() {
        return sequence.size();
    }

    public boolean hasTeams() {
        return !teams.isEmpty();
    }

    public Optional<AnchorHint> anchorHintOrEmpty() {
        return Optional.ofNullable(anchorHint);
    }

    public Optional<TeamOption> team(String label) {
        return teams.stream().filter(t -> t.label().equals(label)).findFirst();
    }

    /** 해당 조의 시퀀스. 조가 없으면 기준 시퀀스 그대로. */
    public List<String> sequenceFor(String teamLabel) {
        if (teamLabel == null) {
            return sequence;
        }
        TeamOption team = team(teamLabel).orElseThrow(() ->
                new IllegalArgumentException("없는 조다: " + id + " / " + teamLabel));
        return WorkRule.rotate(sequence, team.offset());
    }

    /**
     * 기준일을 이 프리셋에 맞게 보정한다.
     *
     * <p>REGULAR은 {@code anchorWeekday}로 스냅한다. 주기 7짜리 시퀀스를 아무 날에나
     * 걸면 "월~금 근무"가 요일과 어긋나기 때문이다. SHIFT는 사용자가 정한 날을 그대로 쓴다.
     */
    public LocalDate snapAnchor(LocalDate date) {
        Objects.requireNonNull(date, "date는 필수다");
        if (anchorWeekday == null) {
            return date;
        }
        return date.with(TemporalAdjusters.previousOrSame(anchorWeekday));
    }

    /**
     * 이 프리셋으로 근무 규칙을 만든다. <b>시퀀스를 복사</b>하므로 이후 프리셋 정의가
     * 바뀌어도 만들어진 규칙은 영향받지 않는다.
     *
     * @param anchorDate    기준일. REGULAR이면 요일로 스냅된다
     * @param effectiveFrom 이 규칙이 적용되기 시작하는 날
     * @param teamLabel     조 이름. 조가 없는 프리셋이면 null
     */
    public WorkRule toWorkRule(LocalDate anchorDate, LocalDate effectiveFrom, String teamLabel) {
        return WorkRule.openEnded(null, snapAnchor(anchorDate), sequenceFor(teamLabel), effectiveFrom);
    }

    /** 조가 없는 프리셋용 축약. */
    public WorkRule toWorkRule(LocalDate anchorDate, LocalDate effectiveFrom) {
        return toWorkRule(anchorDate, effectiveFrom, null);
    }

    /**
     * "이 코드로 시작한 날이 {@code date}였다"는 답으로 기준일을 역산한다.
     * {@link #anchorHint}가 있는 프리셋에서 온보딩에 쓴다.
     *
     * @param teamLabel 조 이름. 조마다 시퀀스가 다르므로 역산 결과도 달라진다
     */
    public Optional<LocalDate> inferAnchor(LocalDate date, String teamLabel) {
        if (anchorHint == null) {
            return Optional.empty();
        }
        List<String> teamSequence = sequenceFor(teamLabel);
        return WorkRule.anchorFromFirstOccurrence(teamSequence, anchorHint.code(), date);
    }
}
