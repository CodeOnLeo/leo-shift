package io.github.codeonleo.leoshift.domain.work;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 캘린더별 근무 코드 사전.
 *
 * <p>이름 · 색 · 시간은 사용자가 정하지만 {@link Category}는 시스템 값이다.
 * 그래야 그룹 타임라인에서 사람마다 다른 코드를 써도 "누가 근무 중이고 누가
 * 휴가인지" 집계할 수 있다.
 *
 * <p>{@code (calendar_id, code)} 유니크는 {@link Leave}와 {@link DayOverride}의
 * 복합 FK가 참조하는 대상이다. 코드 이름을 바꾸면 DB가 ON UPDATE CASCADE로
 * 참조를 따라 고친다. 이전 구현이 임시 코드를 만들어 우회하던 로직이 필요 없다.
 */
@Entity
@Table(name = "schedule_types")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ScheduleType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    /** 대문자만 허용한다. */
    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String color = "#94A3B8";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Category category;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /** 야간 22:00~06:00처럼 자정을 넘는 근무. */
    @Column(name = "crosses_midnight", nullable = false)
    @Builder.Default
    private boolean crossesMidnight = false;

    /** 반차. LEAVE에만 쓸 수 있다. */
    @Column(name = "half_day", nullable = false)
    @Builder.Default
    private boolean halfDay = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 100;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public enum Category {
        /** 실제로 일하는 시간 */
        WORK,
        /** 쉬는 날. 비번 · 휴무 */
        OFF,
        /** 휴가. 연차 · 반차 등 */
        LEAVE
    }

    public boolean countsAsWork() {
        return category == Category.WORK;
    }
}
