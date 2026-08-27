package io.github.codeonleo.leoshift.domain.external;

import io.github.codeonleo.leoshift.domain.calendar.Calendar;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 외부 캘린더 구독 (ICS 피드).
 *
 * <p>{@code (calendar_id, feed_url)} 유니크로 같은 피드를 여러 번 구독하는 것을
 * 막는다. 이전 구현은 중복 구독이 가능했고 동기화마다 이벤트가 통째로 중복됐다.
 *
 * <p><b>보안 주의.</b> 피드를 가져올 때 반드시 사설 IP 대역을 차단하고 리다이렉트
 * 후 재검증해야 한다. 홈서버는 공유기 관리 페이지나 NAS와 같은 내부망에 있어서,
 * 검증 없이 요청을 보내면 내부망 읽기 통로가 된다. 응답 크기 제한도 필요하다.
 */
@Entity
@Table(name = "external_sources")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExternalSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "feed_url", nullable = false)
    private String feedUrl;

    @Column(length = 16)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_mode", nullable = false, length = 16)
    @Builder.Default
    private DisplayMode displayMode = DisplayMode.BADGE;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sync_interval_minutes", nullable = false)
    @Builder.Default
    private int syncIntervalMinutes = 360;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    /** 원격 서버가 준 문구가 들어온다. 화면에 넣을 때 반드시 이스케이핑할 것. */
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public enum DisplayMode { BADGE, INLINE, HIDDEN }
}
