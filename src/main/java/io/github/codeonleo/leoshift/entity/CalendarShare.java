package io.github.codeonleo.leoshift.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "calendar_shares", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"calendar_id", "user_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permission; // VIEW, EDIT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShareStatus status = ShareStatus.PENDING; // PENDING, ACCEPTED, REJECTED

    @Column(nullable = false)
    private LocalDateTime sharedAt;

    @Column
    private LocalDateTime respondedAt;

    @PrePersist
    protected void onCreate() {
        sharedAt = LocalDateTime.now();
    }

    public enum Permission {
        VIEW,   // 읽기 전용
        EDIT    // 읽기 + 쓰기
    }

    public enum ShareStatus {
        PENDING,    // 승인 대기 중
        ACCEPTED,   // 승인됨
        REJECTED    // 거부됨
    }
}
