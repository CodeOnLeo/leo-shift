package io.github.codeonleo.leoshift.domain.user;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 개인 환경 설정.
 *
 * <p>기본 캘린더는 여기 두지 않는다. {@code calendars.is_default}로 옮겼다.
 * 이전 구현의 {@code default_calendar_id}는 ON DELETE가 없어서 공유된 캘린더를
 * 지울 때 FK 위반 500을 냈다.
 *
 * <p>근무 패턴도 여기 두지 않는다. 캘린더에 속하는 정보다.
 */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "default_reminder_minutes", nullable = false)
    @Builder.Default
    private int defaultReminderMinutes = 60;

    /** ISO-8601: 월=1 ... 일=7 */
    @Column(name = "week_starts_on", nullable = false)
    @Builder.Default
    private short weekStartsOn = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Theme theme = Theme.SYSTEM;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public enum Theme { SYSTEM, LIGHT, DARK }
}
