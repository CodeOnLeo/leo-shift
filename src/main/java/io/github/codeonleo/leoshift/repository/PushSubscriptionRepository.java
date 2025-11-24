package io.github.codeonleo.leoshift.repository;

import io.github.codeonleo.leoshift.entity.PushSubscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);
}
