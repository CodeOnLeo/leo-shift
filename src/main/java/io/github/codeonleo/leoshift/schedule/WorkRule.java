package io.github.codeonleo.leoshift.schedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * 반복 근무 규칙.
 *
 * <p>모든 반복 근무를 하나의 원시 타입으로 표현한다.
 * <pre>
 *   code(date) = sequence[ floorMod(date - anchorDate, sequence.size()) ]
 * </pre>
 *
 * <p>요일 규칙은 주기가 7인 순환 패턴일 뿐이므로 별도 타입을 두지 않는다.
 * <ul>
 *   <li>주5일: 월요일 기준, {@code [WORK,WORK,WORK,WORK,WORK,OFF,OFF]}</li>
 *   <li>격주 토요일 근무: 월요일 기준, 주기 14</li>
 *   <li>격일제: 주기 2, {@code [ON,OFF]}</li>
 *   <li>4조 3교대: 주기 12, {@code [D,D,D,A,A,A,N,N,N,O,O,O]}</li>
 * </ul>
 *
 * <p>{@code effectiveFrom}/{@code effectiveTo}로 유효기간을 가진다.
 * 근무 형태가 바뀌어도 과거 근무표가 보존되도록, 규칙을 수정하지 않고 새 규칙을 잇는다.
 *
 * <p>불변이며 스프링에 의존하지 않는다.
 */
public record WorkRule(
        Long id,
        LocalDate anchorDate,
        List<String> sequence,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {

    public WorkRule {
        Objects.requireNonNull(anchorDate, "anchorDate는 필수다");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom은 필수다");
        if (sequence == null || sequence.isEmpty()) {
            throw new IllegalArgumentException("sequence는 최소 1개의 코드를 가져야 한다");
        }
        for (String code : sequence) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("sequence에 빈 코드를 넣을 수 없다: " + sequence);
            }
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "effectiveTo(" + effectiveTo + ")가 effectiveFrom(" + effectiveFrom + ")보다 빠르다");
        }
        sequence = List.copyOf(sequence);
    }

    /** 유효기간이 무기한인 규칙. */
    public static WorkRule openEnded(Long id, LocalDate anchorDate, List<String> sequence, LocalDate effectiveFrom) {
        return new WorkRule(id, anchorDate, sequence, effectiveFrom, null);
    }

    public int cycleLength() {
        return sequence.size();
    }

    /** 이 규칙이 해당 날짜에 적용되는가. 양 끝 포함. */
    public boolean covers(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

    /**
     * 해당 날짜의 시퀀스 위치.
     *
     * <p>기준일 이전 날짜도 올바르게 계산된다. 음수 나머지를 그냥 쓰면
     * 배열 범위를 벗어나므로 floorMod를 쓴다.
     */
    public int indexAt(LocalDate date) {
        long diff = ChronoUnit.DAYS.between(anchorDate, date);
        return (int) Math.floorMod(diff, (long) sequence.size());
    }

    /**
     * 해당 날짜의 근무 코드. <b>유효기간을 보지 않는다.</b>
     *
     * <p>패턴 미리보기처럼 "이 규칙이라면 이 날 무슨 근무인가"를 물을 때 쓴다.
     * 유효기간까지 반영한 해석은 {@link ScheduleResolver}가 한다.
     */
    public String codeAt(LocalDate date) {
        return sequence.get(indexAt(date));
    }

    /** 유효기간을 이 날짜까지로 끊은 새 규칙. 원본은 그대로 둔다. */
    public WorkRule endingOn(LocalDate lastDay) {
        return new WorkRule(id, anchorDate, sequence, effectiveFrom, lastDay);
    }

    // ------------------------------------------------------------------
    // 프리셋·온보딩 보조
    // ------------------------------------------------------------------

    /**
     * 시퀀스를 왼쪽으로 {@code offset}만큼 회전한다.
     *
     * <p>프리셋의 "몇 조인가요?" 선택에 쓴다. 4조 3교대에서 1조가 기준
     * {@code [D,D,D,A,A,A,N,N,N,O,O,O]}일 때 2조(offset 3)는
     * {@code [A,A,A,N,N,N,O,O,O,D,D,D]}가 된다. 기준일은 그대로 두고
     * 시퀀스만 돌리므로, 저장된 규칙만 봐도 그 사람의 근무를 알 수 있다.
     */
    public static List<String> rotate(List<String> sequence, int offset) {
        if (sequence == null || sequence.isEmpty()) {
            throw new IllegalArgumentException("sequence는 비어 있을 수 없다");
        }
        int size = sequence.size();
        int shift = Math.floorMod(offset, size);
        if (shift == 0) {
            return List.copyOf(sequence);
        }
        List<String> rotated = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rotated.add(sequence.get((i + shift) % size));
        }
        return List.copyOf(rotated);
    }

    /**
     * 해당 코드가 연속으로 시작하는 첫 위치.
     *
     * <p>기준일 역산에 쓴다. 사용자에게 "패턴의 1일차가 언제인가요"를 묻는 대신
     * "야간 근무를 시작한 날이 언제인가요"를 묻고, 그 답과 이 위치로 기준일을 계산한다.
     *
     * <p>시퀀스는 순환하므로 run의 시작은 순환 기준으로 판정한다. 예를 들어
     * {@code [N,O,O,N,N]}에서 N의 run 시작은 0이 아니라 3이다(0은 앞의 4와 이어짐).
     *
     * <p>시퀀스 전체가 같은 코드면 run 경계가 없으므로 0을 돌려준다.
     */
    public static OptionalInt firstRunStart(List<String> sequence, String code) {
        if (sequence == null || sequence.isEmpty() || code == null) {
            return OptionalInt.empty();
        }
        int size = sequence.size();
        boolean anyMatch = false;
        for (int i = 0; i < size; i++) {
            if (!code.equals(sequence.get(i))) {
                continue;
            }
            anyMatch = true;
            String previous = sequence.get(Math.floorMod(i - 1, size));
            if (!code.equals(previous)) {
                return OptionalInt.of(i);
            }
        }
        // 전부 같은 코드라 run 경계가 없는 경우
        return anyMatch ? OptionalInt.of(0) : OptionalInt.empty();
    }

    /** 시퀀스의 {@code index}번째 날이 {@code date}였다면 기준일은 언제인가. */
    public static LocalDate anchorFrom(LocalDate date, int index) {
        Objects.requireNonNull(date, "date는 필수다");
        return date.minusDays(index);
    }

    /**
     * "이 코드로 시작한 날이 {@code date}였다"는 답으로 기준일을 역산한다.
     *
     * @return 시퀀스에 해당 코드가 없으면 빈 값
     */
    public static java.util.Optional<LocalDate> anchorFromFirstOccurrence(
            List<String> sequence, String code, LocalDate date) {
        OptionalInt index = firstRunStart(sequence, code);
        if (index.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(anchorFrom(date, index.getAsInt()));
    }
}
