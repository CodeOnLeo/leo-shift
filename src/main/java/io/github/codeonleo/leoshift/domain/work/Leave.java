package io.github.codeonleo.leoshift.domain.work;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import io.github.codeonleo.leoshift.domain.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 휴가 · 부재. 날짜 범위라 "8/15~8/20 연차"가 한 줄이다.
 *
 * <p>대상 사용자 컬럼이 없는 것은 의도적이다. 캘린더가 이미 한 사람의 것이므로
 * 누구의 휴가인지는 캘린더가 결정한다. 이전 구현은 캘린더 단위 예외와 사람 단위
 * 휴가가 한 캘린더 안에 공존하는 모순이 있었다.
 */
@Entity
@Table(name = "leaves")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Leave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * {@code category = LEAVE}인 {@link ScheduleType}의 코드.
     *
     * <p>연관관계로 매핑하지 않는다. DB에서는 {@code (calendar_id, code)}로 가는
     * 복합 FK지만 JPA로 표현하기 번거롭고, 무결성은 DB가 지키고 있다.
     */
    @Column(name = "schedule_type_code", nullable = false, length = 32)
    private String scheduleTypeCode;

    private String note;

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

    public io.github.codeonleo.leoshift.schedule.LeavePeriod toDomain() {
        return new io.github.codeonleo.leoshift.schedule.LeavePeriod(
                id, startDate, endDate, scheduleTypeCode, note);
    }
}
