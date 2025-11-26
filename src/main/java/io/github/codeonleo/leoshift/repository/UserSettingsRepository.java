package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    @Query("SELECT us FROM UserSettings us " +
           "LEFT JOIN FETCH us.defaultCalendar c " +
           "LEFT JOIN FETCH c.owner " +
           "WHERE us.user.id = :userId")
    Optional<UserSettings> findByIdWithCalendar(@Param("userId") Long userId);
}
