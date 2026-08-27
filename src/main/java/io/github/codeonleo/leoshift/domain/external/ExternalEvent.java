package io.github.codeonleo.leoshift.domain.external;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 외부 피드에서 가져온 일정의 캐시.
 *
 * <p>시각을 그대로 보존한다. 이전 구현은 날짜만 남기고 시각을 버려서 구독한
 * 회의가 몇 시인지 알 수 없었다.
 */
@Entity
@Table(name = "external_events")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExternalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private ExternalSource source;

    @Column(nullable = false, length = 512)
    private String uid;

    @Column(length = 500)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "all_day", nullable = false)
    @Builder.Default
    private boolean allDay = false;

    @Column(length = 500)
    private String location;

    private String description;

    @Column(name = "synced_at", nullable = false, insertable = false, updatable = false)
    private Instant syncedAt;
}
