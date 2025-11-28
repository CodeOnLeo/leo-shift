package io.github.codeonleo.leoshift.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "day_memos", indexes = {
        @Index(name = "idx_calendar_date", columnList = "calendar_id, date"),
        @Index(name = "idx_author", columnList = "author_id")
})
@Getter
@Setter
public class DayMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false)
    private LocalDate date;

    @Column(columnDefinition = "text")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "memo_type", nullable = false)
    private MemoType memoType = MemoType.GENERAL;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum MemoType {
        GENERAL,
        ANNIVERSARY
    }
}