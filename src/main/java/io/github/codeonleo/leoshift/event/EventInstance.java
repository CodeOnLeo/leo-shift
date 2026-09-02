package io.github.codeonleo.leoshift.event;

import java.time.Instant;

/**
 * 달력에 실제로 그려지는 한 칸.
 *
 * <p>단발 일정도 회차가 하나인 반복으로 취급한다. 화면이 둘을 구분하지 않아도 되게
 * 하려는 것이다 — 이전 구현에서 반복과 단발이 다른 경로를 타면서 두 화면이 서로
 * 다른 일정을 보여준 적이 있다.
 *
 * @param occurrenceStart 이 회차의 <b>원래</b> 시각. 옮겨진 회차를 다시 가리킬 때 쓰는 열쇠다
 * @param change          이 회차에 손댄 흔적. 취소된 회차도 지우지 않고 표시한다
 */
public record EventInstance(
        Long eventId,
        Long calendarId,
        Instant occurrenceStart,
        Instant startsAt,
        Instant endsAt,
        boolean allDay,
        boolean recurring,
        String title,
        String description,
        String location,
        Change change
) {

    public enum Change { NONE, CANCELLED, MOVED, MODIFIED }

    public boolean isCancelled() {
        return change == Change.CANCELLED;
    }

    /** 조회 구간과 겹치는가. 끝이 구간 시작과 같은 일정은 걸치지 않은 것으로 본다. */
    public boolean overlaps(Instant from, Instant to) {
        return endsAt.isAfter(from) && startsAt.isBefore(to);
    }
}
