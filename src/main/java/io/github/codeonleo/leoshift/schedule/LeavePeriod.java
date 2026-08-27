package io.github.codeonleo.leoshift.schedule;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 휴가·부재 기간. 양 끝을 포함한다.
 *
 * <p>대상 사용자 필드가 없는 것은 의도적이다. 캘린더가 이미 한 사람의 것이므로
 * 누구의 휴가인지는 캘린더가 결정한다.
 */
public record LeavePeriod(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String code,
        String note
) {

    public LeavePeriod {
        Objects.requireNonNull(startDate, "startDate는 필수다");
        Objects.requireNonNull(endDate, "endDate는 필수다");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("휴가 코드는 필수다");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "endDate(" + endDate + ")가 startDate(" + startDate + ")보다 빠르다");
        }
    }

    public static LeavePeriod of(Long id, LocalDate startDate, LocalDate endDate, String code) {
        return new LeavePeriod(id, startDate, endDate, code, null);
    }

    /** 하루짜리 휴가. */
    public static LeavePeriod single(Long id, LocalDate date, String code) {
        return new LeavePeriod(id, date, date, code, null);
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
