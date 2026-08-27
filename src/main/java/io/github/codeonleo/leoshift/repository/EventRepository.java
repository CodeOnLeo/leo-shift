package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.event.Event;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 반복 일정은 DB에서 전개할 수 없으므로 두 갈래로 나눠 가져온다.
 * 단발은 기간으로 거르고, 반복은 후보만 받아 애플리케이션에서 RRULE을 전개한다.
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("select e from Event e where e.id = :id and e.deletedAt is null")
    Optional<Event> findActiveById(@Param("id") Long id);

    /** 기간에 걸치는 단발 일정. */
    @Query("""
            select e from Event e
             where e.calendar.id in :calendarIds
               and e.deletedAt is null
               and e.rrule is null
               and e.startsAt < :to
               and e.endsAt >= :from
             order by e.startsAt
            """)
    List<Event> findSingleOccurrences(@Param("calendarIds") List<Long> calendarIds,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);

    /**
     * 기간에 회차가 있을 수 있는 반복 일정.
     *
     * <p>반복 시작이 조회 끝보다 앞서고, 반복 종료가 없거나 조회 시작보다 뒤인 것.
     * 실제 어느 날에 걸리는지는 RRULE을 전개해 봐야 안다.
     */
    @Query("""
            select e from Event e
             where e.calendar.id in :calendarIds
               and e.deletedAt is null
               and e.rrule is not null
               and e.startsAt < :to
               and (e.recurrenceEnd is null or e.recurrenceEnd >= :from)
             order by e.startsAt
            """)
    List<Event> findRecurringCandidates(@Param("calendarIds") List<Long> calendarIds,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    @Query("select e from Event e where e.calendar.id = :calendarId and e.deletedAt is null order by e.startsAt")
    List<Event> findAllByCalendar(@Param("calendarId") Long calendarId);
}
