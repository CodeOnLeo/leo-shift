package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.ExternalCalendarSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalCalendarSourceRepository extends JpaRepository<ExternalCalendarSource, Long> {

    List<ExternalCalendarSource> findByCalendarOrderByNameAsc(Calendar calendar);

    List<ExternalCalendarSource> findByCalendarAndActiveTrueOrderByNameAsc(Calendar calendar);

    void deleteByCalendar(Calendar calendar);
}
