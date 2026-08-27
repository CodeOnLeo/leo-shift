package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.work.DayOverride;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DayOverrideRepository extends JpaRepository<DayOverride, Long> {

    @Query("select o from DayOverride o where o.calendar.id = :calendarId and o.onDate = :date")
    Optional<DayOverride> findOn(@Param("calendarId") Long calendarId, @Param("date") LocalDate date);

    @Query("""
            select o from DayOverride o
             where o.calendar.id = :calendarId
               and o.onDate between :from and :to
             order by o.onDate
            """)
    List<DayOverride> findInRange(@Param("calendarId") Long calendarId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    @Query("""
            select o from DayOverride o
             where o.calendar.id in :calendarIds
               and o.onDate between :from and :to
             order by o.calendar.id, o.onDate
            """)
    List<DayOverride> findInRangeForCalendars(@Param("calendarIds") List<Long> calendarIds,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    boolean existsByCalendarIdAndScheduleTypeCode(Long calendarId, String scheduleTypeCode);
}
