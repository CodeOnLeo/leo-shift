package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.work.ScheduleType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleTypeRepository extends JpaRepository<ScheduleType, Long> {

    @Query("select t from ScheduleType t where t.calendar.id = :calendarId order by t.sortOrder, t.code")
    List<ScheduleType> findByCalendar(@Param("calendarId") Long calendarId);

    @Query("select t from ScheduleType t where t.calendar.id = :calendarId and t.code = :code")
    Optional<ScheduleType> findByCalendarAndCode(@Param("calendarId") Long calendarId, @Param("code") String code);

    @Query("select t from ScheduleType t where t.calendar.id in :calendarIds order by t.calendar.id, t.sortOrder")
    List<ScheduleType> findByCalendars(@Param("calendarIds") List<Long> calendarIds);

    boolean existsByCalendarId(Long calendarId);
}
