package io.github.codeonleo.leoshift.schedule;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 특정 날짜의 예외.
 *
 * <p>{@code code}가 null이면 메모만 있는 날이다. 이 경우 근무 코드는
 * 아래 계층(휴가 → 규칙)에서 결정되고 메모만 얹힌다.
 */
public record DayOverride(
        Long id,
        LocalDate date,
        String code,
        String note
) {

    public DayOverride {
        Objects.requireNonNull(date, "date는 필수다");
        if (code != null && code.isBlank()) {
            throw new IllegalArgumentException("code는 null이거나 값이 있어야 한다. 빈 문자열은 허용하지 않는다");
        }
        if (code == null && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("코드도 메모도 없는 예외는 존재할 이유가 없다");
        }
    }

    public static DayOverride ofCode(Long id, LocalDate date, String code) {
        return new DayOverride(id, date, code, null);
    }

    public static DayOverride ofNote(Long id, LocalDate date, String note) {
        return new DayOverride(id, date, null, note);
    }
}
