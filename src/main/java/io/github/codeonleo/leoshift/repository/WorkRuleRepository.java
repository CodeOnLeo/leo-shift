package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.work.WorkRule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkRuleRepository extends JpaRepository<WorkRule, Long> {

    /**
     * 기간에 걸치는 규칙 전부를 <b>한 번에</b> 가져온다.
     *
     * <p>이전 구현은 달력을 그릴 때 규칙을 한 번만 조회해 42일 전체에 적용하면서
     * 정확성을 깼고, 날짜 상세 화면은 날짜마다 따로 조회해서 두 화면이 서로 다른
     * 근무를 보여줬다. 구간에 걸치는 규칙을 전부 받아 해석기에 넘기면 둘 다 해결된다.
     */
    @Query("""
            select r from WorkRule r
             where r.calendar.id = :calendarId
               and r.effectiveFrom <= :to
               and (r.effectiveTo is null or r.effectiveTo >= :from)
             order by r.effectiveFrom
            """)
    List<WorkRule> findOverlapping(@Param("calendarId") Long calendarId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    @Query("""
            select r from WorkRule r
             where r.calendar.id in :calendarIds
               and r.effectiveFrom <= :to
               and (r.effectiveTo is null or r.effectiveTo >= :from)
             order by r.calendar.id, r.effectiveFrom
            """)
    List<WorkRule> findOverlappingForCalendars(@Param("calendarIds") List<Long> calendarIds,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /** 설정 화면의 "현재 패턴". */
    @Query("""
            select r from WorkRule r
             where r.calendar.id = :calendarId
             order by r.effectiveFrom desc
             limit 1
            """)
    Optional<WorkRule> findLatest(@Param("calendarId") Long calendarId);

    @Query("select r from WorkRule r where r.calendar.id = :calendarId order by r.effectiveFrom")
    List<WorkRule> findAllByCalendar(@Param("calendarId") Long calendarId);
}
