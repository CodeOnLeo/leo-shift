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
 * 캘린더. 사람 또는 그룹이 소유한다.
 *
 * <p>사용자는 캘린더를 여러 개 가진다. 근무 캘린더만 직장에 공유하면 개인 일정은
 * 애초에 새어 나가지 않으므로, 세밀한 권한 제어 없이 공개 범위가 나뉜다.
 *
 * <p>본인 화면에서는 자기 캘린더 전부가 겹쳐 보인다. 분리는 공유할 때만 의미가 있다.
 */
@Entity
@Table(name = "calendars")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Calendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유자는 사용자이거나 그룹이거나, 정확히 하나다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private User ownerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_group_id")
    private Group ownerGroup;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Column(length = 16)
    private String color;

    /** WORK 캘린더에만 근무 규칙과 휴가가 붙는다. 사용자당 보통 하나다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(name = "time_zone", nullable = false, length = 64)
    @Builder.Default
    private String timeZone = "Asia/Seoul";

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public enum Kind { WORK, GENERAL }

    public boolean isOwnedByGroup() {
        return ownerGroup != null;
    }
}
