package io.github.codeonleo.leoshift.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "external_calendar_sources")
@Getter
@Setter
public class ExternalCalendarSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calendar_id", nullable = false)
    private Calendar calendar;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "feed_url", nullable = false, columnDefinition = "text")
    private String feedUrl;

    @Column(nullable = false, length = 20)
    private String color = "#5E5CE6";

    @Enumerated(EnumType.STRING)
    @Column(name = "display_mode", nullable = false, length = 32)
    private DisplayMode displayMode = DisplayMode.TAG;

    @Column(name = "date_text_color", nullable = false, length = 20)
    private String dateTextColor = "#FF3B30";

    @Column(name = "border_color", nullable = false, length = 20)
    private String borderColor = "#FF3B30";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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

    public enum DisplayMode {
        TAG,
        DATE_STYLE,
        DATE_TEXT
    }
}
