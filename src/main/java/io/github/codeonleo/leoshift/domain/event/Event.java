package io.github.codeonleo.leoshift.domain.event;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시간을 가진 일정.
 *
 * <p>과외 수업처럼 임의 시각에 시작하는 반복 일정도 이 하나로 표현한다.
 * 별도 기능이 아니라 반복 일정을 제대로 만들면 나오는 것들이다.
 *
 * <p>근무 규칙과는 다른 원시 타입이다. 근무는 {@code (기준일, 주기, 시퀀스)}이고
 * 일정은 RRULE이다. 근무는 하루에 하나이고 전체가 한 덩어리로 순환하지만,
 * 일정은 하루에 여러 개이고 건마다 따로 반복하며 개별 회차 변경이 잦다.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(nullable = false, length = 200)
    private String title;

    private String description;

    @Column(length = 200)
    private String location;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "all_day", nullable = false)
    @Builder.Default
    private boolean allDay = false;

    /**
     * 반복 전개 기준 시간대.
     *
     * <p>시각만으로는 부족하다. "매주 화 20:30"이 서머타임이나 시간대 이동에서도
     * 벽시계 기준으로 유지되려면 어느 시간대의 20:30인지 알아야 한다.
     */
    @Column(name = "time_zone", nullable = false, length = 64)
    @Builder.Default
    private String timeZone = "Asia/Seoul";

    /** RFC 5545 RRULE. 단발 일정이면 null. */
    private String rrule;

    /** 반복의 마지막 시각. 무한 반복이면 null. */
    @Column(name = "recurrence_end")
    private Instant recurrenceEnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isRecurring() {
        return rrule != null && !rrule.isBlank();
    }

    /** 전개 엔진이 쓰는 값으로. 엔진은 JPA를 모른다. */
    public io.github.codeonleo.leoshift.event.EventDefinition toDomain() {
        return new io.github.codeonleo.leoshift.event.EventDefinition(
                id, calendar.getId(), title, description, location,
                startsAt, endsAt, allDay,
                java.time.ZoneId.of(timeZone), rrule, recurrenceEnd);
    }
}
