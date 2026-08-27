package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.event.EventOccurrence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventOccurrenceRepository extends JpaRepository<EventOccurrence, Long> {

    Optional<EventOccurrence> findByEventIdAndOriginalStart(Long eventId, Instant originalStart);

    /** 여러 반복 일정의 예외를 한 번에. RRULE 전개 결과에 덮어쓸 때 쓴다. */
    @Query("select o from EventOccurrence o where o.event.id in :eventIds")
    List<EventOccurrence> findByEventIds(@Param("eventIds") List<Long> eventIds);

    List<EventOccurrence> findByEventId(Long eventId);
}
