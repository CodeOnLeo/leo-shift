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

    /** 달력에 겹쳐 그릴 피드. 숨김으로 둔 것은 애초에 가져오지 않는다. */
    @Query("""
            select s from ExternalSource s
             where s.calendar.id in :calendarIds
               and s.active = true
               and s.displayMode <> io.github.codeonleo.leoshift.domain.external.ExternalSource.DisplayMode.HIDDEN
             order by s.name
            """)
    List<ExternalSource> findVisibleByCalendars(@Param("calendarIds") List<Long> calendarIds);

    /** 동기화 대상. 스케줄러가 쓴다. */
    @Query("""
            select s from ExternalSource s
             where s.active = true
               and (s.lastSyncedAt is null or s.lastSyncedAt < :staleBefore)
             order by s.lastSyncedAt nulls first
            """)
    List<ExternalSource> findDueForSync(@Param("staleBefore") Instant staleBefore);
}
