package io.github.codeonleo.leoshift.domain.user;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대소문자를 구분하지 않는다. 저장 전에 정규화할 것. */
    @Column(nullable = false)
    private String email;

    /** 소셜 로그인 전용 계정은 null. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String nickname;

    @Column(name = "picture_url")
    private String pictureUrl;

    /** 겹쳐 보기에서 이 사람을 구분하는 색. */
    @Column(name = "color_tag", length = 16)
    private String colorTag;

    @Column(name = "time_zone", nullable = false, length = 64)
    @Builder.Default
    private String timeZone = "Asia/Seoul";

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String locale = "ko-KR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.ACTIVE;

    /**
     * 비밀번호 변경·계정 정지 시 올린다. 발급된 모든 토큰이 즉시 무효가 된다.
     * 이전 구현은 토큰을 폐기할 수단이 아예 없었다.
     */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private int tokenVersion = 0;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public enum Role { USER, ADMIN }

    public enum Status { ACTIVE, SUSPENDED }

    public boolean isActive() {
        return status == Status.ACTIVE && deletedAt == null;
    }
}
