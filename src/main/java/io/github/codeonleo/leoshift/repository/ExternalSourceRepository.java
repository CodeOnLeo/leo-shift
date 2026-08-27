package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.external.ExternalSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalSourceRepository extends JpaRepository<ExternalSource, Long> {

    @Query("select s from ExternalSource s where s.calendar.id = :calendarId order by s.name")
    List<ExternalSource> findByCalendar(@Param("calendarId") Long calendarId);

    Optional<ExternalSource> findByCalendarIdAndFeedUrl(Long calendarId, String feedUrl);

    /** 동기화 대상. 스케줄러가 쓴다. */
    @Query("""
            select s from ExternalSource s
             where s.active = true
               and (s.lastSyncedAt is null or s.lastSyncedAt < :staleBefore)
             order by s.lastSyncedAt nulls first
            """)
    List<ExternalSource> findDueForSync(@Param("staleBefore") Instant staleBefore);
}
