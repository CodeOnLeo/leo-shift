package io.github.codeonleo.leoshift.domain.group;

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
 * 프로젝트 · 직장 · 가족 · 친구 같은 관계.
 *
 * <p><b>그룹은 데이터를 담지 않는다.</b> "이 사람들의 캘린더를 이 기간 동안 겹쳐서
 * 보여줘"라는 뷰의 정의일 뿐이다. 근무와 휴가는 각자의 개인 캘린더에 있다.
 * 그래서 프로젝트를 옮겨도 데이터가 따라간다.
 */
@Entity
@Table(name = "groups")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 삭제 시 그룹도 함께 사라진다. 계정 삭제 흐름에서 소유권 이전을 먼저 제안할 것. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Kind kind = Kind.PROJECT;

    private String description;

    @Column(length = 16)
    private String color;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public enum Kind { PROJECT, WORKPLACE, FAMILY, FRIENDS, OTHER }
}
