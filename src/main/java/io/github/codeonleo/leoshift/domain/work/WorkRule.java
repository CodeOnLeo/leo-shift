package io.github.codeonleo.leoshift.domain.work;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 반복 근무 규칙의 저장 형태.
 *
 * <p>계산은 {@link io.github.codeonleo.leoshift.schedule.WorkRule}이 한다.
 * 이름이 같은 클래스가 둘인 이유는 하나는 영속화 대상이고 하나는 스프링 의존이 없는
 * 순수 계산 값이기 때문이다. {@link #toDomain()}으로 변환한다.
 *
 * <p>유효기간이 겹치는 규칙은 DB의 배제 제약이 막는다. 겹침이 불가능하면 그날의
 * 규칙이 항상 하나로 정해지므로, 화면마다 다른 근무가 나오는 일이 생기지 않는다.
 */
@Entity
@Table(name = "work_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    /** 시퀀스 0번이 적용되는 날. */
    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Column(name = "cycle_length", nullable = false)
    private int cycleLength;

    /**
     * {@link ScheduleType#getCode()} 배열.
     *
     * <p>JSONB라 FK가 걸리지 않는다. 코드 이름을 바꾸거나 지울 때는 이 컬럼을
     * 애플리케이션이 직접 갱신해야 한다. ({@code leaves}와 {@code day_overrides}는
     * 복합 FK가 처리한다.)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "code_sequence", nullable = false)
    private List<String> codeSequence;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** null이면 무기한. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** 어떤 프리셋에서 시작했는지. 참조가 아니라 출처 기록이다. */
    @Column(name = "source_preset_id", length = 64)
    private String sourcePresetId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    /** 계산용 값 객체로 변환한다. */
    public io.github.codeonleo.leoshift.schedule.WorkRule toDomain() {
        return new io.github.codeonleo.leoshift.schedule.WorkRule(
                id, anchorDate, codeSequence, effectiveFrom, effectiveTo);
    }
}
