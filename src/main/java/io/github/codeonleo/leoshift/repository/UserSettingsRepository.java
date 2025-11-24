package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}
