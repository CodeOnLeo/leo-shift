package io.github.codeonleo.leoshift.domain.event;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 반복 중 예외가 생긴 회차. iCalendar의 RECURRENCE-ID 방식이다.
 *
 * <p>휴강 · 보강, "이번 주 회의만 시간 변경"이 전부 여기로 들어온다.
 * 예외가 생긴 회차만 저장하므로 반복 전체를 펼쳐 저장할 필요가 없다.
 *
 * <p>취소를 삭제가 아니라 상태로 남기므로 달력에 "휴강"으로 표시할 수 있고
 * 되돌리기도 쉽다.
 */
@Entity
@Table(name = "event_occurrences")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /** 원래 몇 회차인지 식별한다. */
    @Column(name = "original_start", nullable = false)
    private Instant originalStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    /** 옮긴 경우의 새 시각. CANCELLED면 null이어도 된다. */
    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    /** 이 회차만 다른 제목. */
    @Column(length = 200)
    private String title;

    private String note;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public enum Status { CANCELLED, MOVED, MODIFIED }

    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }
}
