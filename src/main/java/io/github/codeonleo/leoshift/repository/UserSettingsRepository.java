package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.domain.user.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}
