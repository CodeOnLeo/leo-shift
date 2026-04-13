package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarShareGrant;
import io.github.codeonleo.leoshift.entity.ShareGroup;
import io.github.codeonleo.leoshift.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarShareGrantRepository extends JpaRepository<CalendarShareGrant, Long> {

    @Query("""
            SELECT grant FROM CalendarShareGrant grant
            LEFT JOIN FETCH grant.targetUser
            LEFT JOIN FETCH grant.targetGroup
            WHERE grant.calendar = :calendar
            """)
    List<CalendarShareGrant> findByCalendar(@Param("calendar") Calendar calendar);

    Optional<CalendarShareGrant> findByCalendarAndTargetUser(Calendar calendar, User user);

    Optional<CalendarShareGrant> findByCalendarAndTargetGroup(Calendar calendar, ShareGroup group);

    @Query("""
            SELECT grant FROM CalendarShareGrant grant
            LEFT JOIN FETCH grant.calendar calendar
            LEFT JOIN FETCH calendar.owner
            WHERE grant.targetUser = :user
            """)
    List<CalendarShareGrant> findByTargetUser(@Param("user") User user);

    @Query("""
            SELECT grant FROM CalendarShareGrant grant
            LEFT JOIN FETCH grant.calendar calendar
            LEFT JOIN FETCH calendar.owner
            LEFT JOIN FETCH grant.targetGroup
            WHERE grant.targetGroup IN :groups
            """)
    List<CalendarShareGrant> findByTargetGroupIn(@Param("groups") List<ShareGroup> groups);

    void deleteByCalendar(Calendar calendar);
}
