package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarWeeklyRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarWeeklyRuleRepository extends JpaRepository<CalendarWeeklyRule, Long> {

    List<CalendarWeeklyRule> findByCalendarOrderByDayOfWeekAsc(Calendar calendar);

    Optional<CalendarWeeklyRule> findByCalendarAndDayOfWeek(Calendar calendar, Integer dayOfWeek);

    boolean existsByCalendar(Calendar calendar);

    void deleteByCalendar(Calendar calendar);
}
