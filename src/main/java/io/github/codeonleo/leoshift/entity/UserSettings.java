package io.github.codeonleo.leoshift.entity;

import jakarta.persistence.*;
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

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_calendar_id")
    private Calendar defaultCalendar;

    @Column(name = "pattern_codes")
    private String patternCodes;

    @Column(name = "pattern_start_date")
    private LocalDate patternStartDate;

    @Column(name = "default_notification_minutes")
    private Integer defaultNotificationMinutes;
}
