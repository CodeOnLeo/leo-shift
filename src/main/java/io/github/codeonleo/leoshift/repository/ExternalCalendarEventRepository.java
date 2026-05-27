package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.ExternalCalendarEvent;
import io.github.codeonleo.leoshift.entity.ExternalCalendarSource;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalCalendarEventRepository extends JpaRepository<ExternalCalendarEvent, Long> {

    void deleteBySource(ExternalCalendarSource source);

    @Query("""
            SELECT event FROM ExternalCalendarEvent event
            JOIN FETCH event.source source
            WHERE source.calendar = :calendar
              AND source.active = true
              AND event.startDate <= :end
              AND event.endDate >= :start
            ORDER BY event.startDate ASC, event.title ASC, event.id ASC
            """)
    List<ExternalCalendarEvent> findVisibleEvents(@Param("calendar") Calendar calendar,
                                                  @Param("start") LocalDate start,
                                                  @Param("end") LocalDate end);
}
