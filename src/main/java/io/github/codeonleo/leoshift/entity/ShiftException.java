package io.github.codeonleo.leoshift.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "exceptions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"calendar_id", "date"})
})
@Getter
@Setter
public class ShiftException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "custom_code")
    private String customCode;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "anniversary_memo", columnDefinition = "text")
    private String anniversaryMemo;

    @Column(name = "repeat_yearly")
    private boolean repeatYearly;

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
}
