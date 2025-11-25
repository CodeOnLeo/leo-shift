package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.ShiftException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShiftExceptionRepository extends JpaRepository<ShiftException, Long> {

    Optional<ShiftException> findByCalendarAndDate(Calendar calendar, LocalDate date);

    List<ShiftException> findByCalendarAndDateBetween(Calendar calendar, LocalDate start, LocalDate end);

    @Query("select e from ShiftException e where e.calendar = :calendar and e.repeatYearly = true")
    List<ShiftException> findYearlyRepeating(@Param("calendar") Calendar calendar);

    @Query("select e from ShiftException e where e.calendar = :calendar and e.repeatYearly = true and MONTH(e.date) = :month")
    List<ShiftException> findYearlyEntriesForMonth(@Param("calendar") Calendar calendar, @Param("month") int month);
}
