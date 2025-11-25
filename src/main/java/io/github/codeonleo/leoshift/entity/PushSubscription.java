package io.github.codeonleo.leoshift.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "push_subscriptions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "endpoint"})
})
@Getter
@Setter
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "text", nullable = false)
    private String endpoint;

    @Column(columnDefinition = "text", nullable = false)
    private String p256dh;

    @Column(columnDefinition = "text", nullable = false)
    private String auth;
}
