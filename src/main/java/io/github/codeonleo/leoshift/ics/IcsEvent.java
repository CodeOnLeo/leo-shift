package io.github.codeonleo.leoshift.ics;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

/**
 * 외부 피드에서 읽어낸 VEVENT 하나.
 *
 * <p>엔티티가 아니라 값이다. {@code schedule} · {@code event} 패키지와 같은 방식으로
 * 스프링도 JPA도 모른다. ICS 파싱은 틀리기 쉬운 데 비해 눈으로 확인이 안 되는
 * 영역이라, 컨테이너 없이 빠르게 돌릴 수 있어야 한다.
 *
 * <p>시각을 {@link Instant}로 정규화해서 담는다. 이전 구현은 날짜만 남기고 시각을
 * 버려서 구독한 회의가 몇 시인지 알 수 없었다.
 *
 * @param recurrenceId 이 줄이 반복 중 <b>한 회차만</b> 고친 것이라면 그 회차의 원래 시각.
 *                     아니면 null. 부모 시리즈는 이 시각을 건너뛰어야 한다
 * @param zone         DTSTART가 적혀 있던 시간대. <b>반복 전개에 반드시 필요하다</b> —
 *                     절대 시각에 일주일을 더하면 서머타임 너머에서 시계가 한 시간 밀린다
 * @param rrule        원문 그대로의 RRULE. 우리가 못 읽는 규칙도 있으므로 해석은 미룬다
 * @param until        반복 종료 시각. RRULE의 UNTIL에서 뽑는다.
 *                     <b>여기서 뽑지 않으면 무한 반복이 된다</b> — RRULE 파서는 UNTIL을 무시한다
 * @param exDates      제외할 회차 시각 (EXDATE)
 * @param cancelled    {@code STATUS:CANCELLED}. 저장하지 않고 버린다
 */
public record IcsEvent(
        String uid,
        Instant recurrenceId,
        String summary,
        String description,
        String location,
        Instant startsAt,
        Instant endsAt,
        boolean allDay,
        ZoneId zone,
        String rrule,
        Instant until,
        Set<Instant> exDates,
        boolean cancelled
) {

    public IcsEvent {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("UID가 없다");
        }
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("일정에 시작과 끝이 필요하다");
        }
        if (endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("종료가 시작보다 빠르다");
        }
        if (zone == null) {
            throw new IllegalArgumentException("일정에 시간대가 필요하다");
        }
        exDates = exDates == null ? Set.of() : Set.copyOf(exDates);
    }

    public boolean isRecurring() {
        return rrule != null && !rrule.isBlank();
    }

    /** 반복 중 한 회차만 고친 줄인가. */
    public boolean isOverride() {
        return recurrenceId != null;
    }
}
