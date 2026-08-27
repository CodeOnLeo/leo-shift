package io.github.codeonleo.leoshift.schedule;

import java.time.LocalDate;

/**
 * 해석된 하루.
 *
 * @param code     그날의 근무 코드. 정해지지 않았으면 null
 * @param source   코드가 어디서 왔는지. 화면에서 "왜 이 근무인지"를 보여주거나,
 *                 편집 시 무엇을 고쳐야 하는지 판단하는 데 쓴다
 * @param sourceId 코드를 제공한 규칙/휴가/예외의 id
 * @param note     그날의 메모. 코드 출처와 무관하게 예외에서 온다
 */
public record ResolvedDay(
        LocalDate date,
        String code,
        Source source,
        Long sourceId,
        String note
) {

    public enum Source {
        /** 반복 근무 규칙에서 계산됨 */
        RULE,
        /** 휴가 기간에 걸림 */
        LEAVE,
        /** 날짜별 예외로 덮어씀 */
        OVERRIDE,
        /** 적용되는 규칙이 없음 */
        NONE
    }

    public boolean hasCode() {
        return code != null;
    }

    public boolean hasNote() {
        return note != null && !note.isBlank();
    }
}
