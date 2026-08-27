package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.work.Leave;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRepository extends JpaRepository<Leave, Long> {

    /** 기간에 걸치는 휴가. 범위끼리 겹치기만 하면 포함된다. */
    @Query("""
            select l from Leave l
             where l.calendar.id = :calendarId
               and l.startDate <= :to
               and l.endDate >= :from
             order by l.startDate
            """)
    List<Leave> findOverlapping(@Param("calendarId") Long calendarId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    /** 그룹 타임라인용. 여러 사람의 휴가를 한 번에 가져온다. */
    @Query("""
            select l from Leave l
             where l.calendar.id in :calendarIds
               and l.startDate <= :to
               and l.endDate >= :from
             order by l.calendar.id, l.startDate
            """)
    List<Leave> findOverlappingForCalendars(@Param("calendarIds") List<Long> calendarIds,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    boolean existsByCalendarIdAndScheduleTypeCode(Long calendarId, String scheduleTypeCode);
}
