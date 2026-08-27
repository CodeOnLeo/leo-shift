package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.calendar.CalendarFeedToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarFeedTokenRepository extends JpaRepository<CalendarFeedToken, Long> {

    @Query("select t from CalendarFeedToken t join fetch t.calendar where t.token = :token and t.revokedAt is null")
    Optional<CalendarFeedToken> findUsable(@Param("token") UUID token);

    @Query("select t from CalendarFeedToken t where t.calendar.id = :calendarId and t.revokedAt is null")
    List<CalendarFeedToken> findActiveFor(@Param("calendarId") Long calendarId);
}
