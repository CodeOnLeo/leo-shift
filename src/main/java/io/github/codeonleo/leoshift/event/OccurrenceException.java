package io.github.codeonleo.leoshift.event;

import java.time.Instant;

/**
 * 반복 중 한 회차만 달라진 것.
 *
 * <p>휴강 · 보강, "이번 주 회의만 시간 변경"이 전부 이 하나로 표현된다.
 * 예외가 생긴 회차만 저장하므로 반복 전체를 펼쳐둘 필요가 없다.
 *
 * @param originalStart 어느 회차인지 식별하는 원래 시각. 옮겨도 이 값은 바뀌지 않는다
 */
public record OccurrenceException(
        Long eventId,
        Instant originalStart,
        Kind kind,
        Instant startsAt,
        Instant endsAt,
        String title,
        String note
) {

    public enum Kind {
        /** 휴강. 지우지 않고 남겨서 달력에 "취소됨"으로 보여준다. */
        CANCELLED,
        /** 다른 시각으로 옮김. */
        MOVED,
        /** 시각은 그대로, 제목이나 메모만 다름. */
        MODIFIED
    }

    public OccurrenceException {
        if (originalStart == null) {
            throw new IllegalArgumentException("어느 회차인지가 필요하다");
        }
        if (kind != Kind.CANCELLED && startsAt == null) {
            throw new IllegalArgumentException("옮기거나 고친 회차에는 시각이 필요하다");
        }
    }
}
