package io.github.codeonleo.leoshift.domain.calendar;

import io.github.codeonleo.leoshift.domain.group.Group;
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
 * 공유 한 건 = 누구에게(사람 또는 그룹) × 무엇을(캘린더) × 어떻게(권한 · 공개).
 *
 * <p>화면의 "근무만 / 바쁨만 / 전체" 3단계는 두 축의 조합으로 구현된다.
 * "근무만"은 WORK 캘린더만 공유하는 것이므로 이 표의 {@code visibility}는 두 값뿐이다.
 *
 * <p>이전 구현은 공유가 세 벌(shares · grants · groups)로 나뉘어 있었고,
 * 권한이 더해지기만 해서 거절을 무효화하고 개인을 보기 전용으로 낮출 수 없었다.
 */
@Entity
@Table(name = "calendar_shares")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CalendarShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    /** 대상은 사람이거나 그룹이거나, 정확히 하나다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grantee_user_id")
    private User granteeUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grantee_group_id")
    private Group granteeGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Permission permission = Permission.VIEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Visibility visibility = Visibility.FULL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public enum Permission { VIEW, EDIT }

    /** FULL은 제목·메모까지, BUSY_ONLY는 시간대만 "바쁨"으로 보여준다. */
    public enum Visibility { FULL, BUSY_ONLY }

    public enum Status { PENDING, ACCEPTED, REJECTED, REVOKED }

    public boolean isActive() {
        return status == Status.ACCEPTED;
    }
}
