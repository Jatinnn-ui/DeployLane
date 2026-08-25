package com.deployforge.webhook;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByDeliveryId(String deliveryId);

    boolean existsByDeliveryId(String deliveryId);

    @Modifying
    @Query("delete from WebhookDelivery d where d.receivedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
