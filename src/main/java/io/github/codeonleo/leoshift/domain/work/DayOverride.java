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
 * 날짜별 예외와 메모. 규칙과 휴가를 덮어쓴다.
 *
 * <p>하루 해석 순서: {@link WorkRule} → {@link Leave} → 이 표.
 *
 * <p>{@code scheduleTypeCode}가 null이면 메모만 있는 날이고, 근무 코드는 아래
 * 계층에서 결정된다.
 *
 * <p>{@code @Version}으로 동시 편집을 막는다. 이전 구현은 잠금이 없어서 편집
 * 권한을 가진 두 사람이 같은 날을 고치면 나중 사람이 앞사람 메모를 지웠다.
 */
@Entity
@Table(name = "day_overrides")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DayOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(name = "on_date", nullable = false)
    private LocalDate onDate;

    /** null이면 메모만 있는 날. */
    @Column(name = "schedule_type_code", length = 32)
    private String scheduleTypeCode;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User author;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    public io.github.codeonleo.leoshift.schedule.DayOverride toDomain() {
        return new io.github.codeonleo.leoshift.schedule.DayOverride(id, onDate, scheduleTypeCode, note);
    }
}
