package io.github.codeonleo.leoshift.event;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 전개에 필요한 만큼의 일정. 엔티티가 아니라 값이다.
 *
 * <p>{@code schedule} 패키지와 같은 방식이다 — 전개 로직이 JPA나 스프링을 모르게
 * 두면 시간대와 반복이라는 까다로운 부분을 컨테이너 없이 테스트할 수 있다.
 *
 * @param zone 반복을 전개하는 기준 시간대. 절대 시각만으로는 "매주 화 20:30"을
 *             서머타임 너머로 유지할 수 없다
 */
public record EventDefinition(
        Long id,
        Long calendarId,
        String title,
        String description,
        String location,
        Instant startsAt,
        Instant endsAt,
        boolean allDay,
        ZoneId zone,
        String rrule,
        Instant recurrenceEnd
) {

    public EventDefinition {
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("일정에 시작과 끝이 필요하다");
        }
        if (endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("종료가 시작보다 빠르다");
        }
        if (zone == null) {
            throw new IllegalArgumentException("일정에 시간대가 필요하다");
        }
    }

    public boolean isRecurring() {
        return rrule != null && !rrule.isBlank();
    }

    /** 한 회차의 길이. 반복 회차는 전부 이 길이를 쓴다. */
    public Duration duration() {
        return Duration.between(startsAt, endsAt);
    }
}
