package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.DayMemo;
import io.github.codeonleo.leoshift.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DayMemoRepository extends JpaRepository<DayMemo, Long> {

    @Query("select m from DayMemo m left join fetch m.author where m.calendar = :calendar and m.date = :date order by m.createdAt asc")
    List<DayMemo> findByCalendarAndDate(@Param("calendar") Calendar calendar, @Param("date") LocalDate date);

    @Query("select m from DayMemo m left join fetch m.author where m.calendar = :calendar and m.date between :start and :end order by m.date asc, m.createdAt asc")
    List<DayMemo> findByCalendarAndDateBetween(@Param("calendar") Calendar calendar, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("select m from DayMemo m left join fetch m.author where m.calendar = :calendar and m.date = :date and m.author = :author")
    Optional<DayMemo> findByCalendarAndDateAndAuthor(@Param("calendar") Calendar calendar, @Param("date") LocalDate date, @Param("author") User author);

    void deleteByCalendar(Calendar calendar);

    @Query("select m from DayMemo m where m.id = :id and m.author = :author")
    Optional<DayMemo> findByIdAndAuthor(@Param("id") Long id, @Param("author") User author);
}
