package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.Calendar;
import io.github.codeonleo.leoshift.entity.CalendarShare;
import io.github.codeonleo.leoshift.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarShareRepository extends JpaRepository<CalendarShare, Long> {

    List<CalendarShare> findByUserAndStatus(User user, CalendarShare.ShareStatus status);

    List<CalendarShare> findByUser(User user);

    List<CalendarShare> findByCalendar(Calendar calendar);

    Optional<CalendarShare> findByCalendarAndUser(Calendar calendar, User user);

    @Query("SELECT cs FROM CalendarShare cs WHERE cs.user = :user AND cs.status = 'ACCEPTED'")
    List<CalendarShare> findAcceptedSharesByUser(User user);
}
