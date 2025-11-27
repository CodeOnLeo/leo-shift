package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarPattern;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarPatternRepository extends JpaRepository<CalendarPattern, Long> {

    List<CalendarPattern> findByCalendarOrderByPatternStartDateAsc(Calendar calendar);

    Optional<CalendarPattern> findTopByCalendarOrderByPatternStartDateDesc(Calendar calendar);

    Optional<CalendarPattern> findTopByCalendarAndPatternStartDateLessThanEqualOrderByPatternStartDateDesc(Calendar calendar, LocalDate date);

    void deleteByCalendar(Calendar calendar);
}
