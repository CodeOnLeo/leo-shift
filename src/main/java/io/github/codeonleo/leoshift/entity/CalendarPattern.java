package io.github.codeonleo.leoshift.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "calendar_patterns", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"calendar_id", "pattern_start_date"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(name = "pattern_codes", nullable = false, columnDefinition = "text")
    private String patternCodes;

    @Column(name = "pattern_start_date", nullable = false)
    private LocalDate patternStartDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
