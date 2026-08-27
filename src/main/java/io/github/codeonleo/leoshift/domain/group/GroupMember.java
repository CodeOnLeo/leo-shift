package io.github.codeonleo.leoshift.domain.group;

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
 * 그룹 참여 이력.
 *
 * <p><b>기간이 있는 것이 핵심이다.</b> "프로젝트 단위로 변경되는 인원"을 여기서
 * 해결한다. 사람을 지우는 게 아니라 {@code leftOn}을 적는다. 8월 화면에는 8월에
 * 소속된 사람만 나오고, 6월 화면에는 6월에 있던 사람이 그대로 나온다.
 *
 * <p>나갔다가 다시 들어올 수 있으므로 한 사람이 같은 그룹에 여러 행을 가질 수 있다.
 * 다만 활성 멤버십은 하나뿐이다(부분 유니크 인덱스).
 */
@Entity
@Table(name = "group_members")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Role role = Role.MEMBER;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    /** null이면 현재 소속 중. */
    @Column(name = "left_on")
    private LocalDate leftOn;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public enum Role { OWNER, MEMBER }

    /** 해당 날짜에 이 그룹에 소속돼 있었는가. */
    public boolean activeOn(LocalDate date) {
        return !date.isBefore(joinedOn) && (leftOn == null || !date.isAfter(leftOn));
    }
}
