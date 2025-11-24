package io.github.codeonleo.leoshift.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettings {

    @Id
    private Long id;

    @Column(name = "pattern_codes")
    private String patternCodes;

    @Column(name = "pattern_start_date")
    private LocalDate patternStartDate;

    @Column(name = "default_notification_minutes")
    private Integer defaultNotificationMinutes;
}
