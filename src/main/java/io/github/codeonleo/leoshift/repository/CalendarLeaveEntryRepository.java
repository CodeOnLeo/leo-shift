package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarLeaveEntry;
import io.github.codeonleo.leoshift.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarLeaveEntryRepository extends JpaRepository<CalendarLeaveEntry, Long> {

    @Query("""
            SELECT entry FROM CalendarLeaveEntry entry
            LEFT JOIN FETCH entry.targetUser
            LEFT JOIN FETCH entry.createdByUser
            WHERE entry.calendar = :calendar AND entry.date = :date
            ORDER BY COALESCE(entry.targetUser.nickname, entry.targetUser.name), entry.id
            """)
    List<CalendarLeaveEntry> findByCalendarAndDate(@Param("calendar") Calendar calendar, @Param("date") LocalDate date);

    @Query("""
            SELECT entry FROM CalendarLeaveEntry entry
            LEFT JOIN FETCH entry.targetUser
            LEFT JOIN FETCH entry.createdByUser
            WHERE entry.calendar = :calendar AND entry.date BETWEEN :start AND :end
            ORDER BY entry.date ASC, COALESCE(entry.targetUser.nickname, entry.targetUser.name), entry.id
            """)
    List<CalendarLeaveEntry> findByCalendarAndDateBetween(@Param("calendar") Calendar calendar,
                                                          @Param("start") LocalDate start,
                                                          @Param("end") LocalDate end);

    @Query("""
            SELECT entry FROM CalendarLeaveEntry entry
            LEFT JOIN FETCH entry.targetUser
            LEFT JOIN FETCH entry.createdByUser
            WHERE entry.calendar = :calendar AND entry.date = :date AND entry.targetUser = :targetUser
            """)
    Optional<CalendarLeaveEntry> findByCalendarAndDateAndTargetUser(@Param("calendar") Calendar calendar,
                                                                    @Param("date") LocalDate date,
                                                                    @Param("targetUser") User targetUser);

    void deleteByCalendar(Calendar calendar);
}
