package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.ScheduleType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleTypeRepository extends JpaRepository<ScheduleType, Long> {

    List<ScheduleType> findByCalendarOrderBySortOrderAscCodeAsc(Calendar calendar);

    Optional<ScheduleType> findByCalendarAndCodeIgnoreCase(Calendar calendar, String code);

    boolean existsByCalendar(Calendar calendar);

    void deleteByCalendar(Calendar calendar);
}
