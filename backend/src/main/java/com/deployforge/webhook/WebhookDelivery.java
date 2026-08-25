package com.deployforge.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Record of a processed GitHub delivery, used purely for idempotency.
 *
 * <p>GitHub retries deliveries, and a retry must not produce a second deployment of the same commit. The
 * unique constraint on {@code delivery_id} is the actual guard - the row is inserted first and a
 * duplicate insert is what tells us this delivery was already handled.
 *
 * <p>Not extending the shared audited base class: {@code received_at} already is this row's creation
 * time, and a second timestamp column would say the same thing twice.
 */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, unique = true, length = 128)
    private String deliveryId;

    @Column(name = "event", nullable = false, length = 64)
    private String event;

    @Column(name = "action", length = 64)
    private String action;

    @Column(name = "repository_full_name", length = 512)
    private String repositoryFullName;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "result", length = 255)
    private String result;

    @Column(name = "received_at", nullable = false)
    private java.time.Instant receivedAt;

    protected WebhookDelivery() {}

    public WebhookDelivery(String deliveryId, String event, String action, String repositoryFullName) {
        this.deliveryId = deliveryId;
        this.event = event;
        this.action = action;
        this.repositoryFullName = repositoryFullName;
        this.receivedAt = java.time.Instant.now();
        this.processed = false;
    }

    public void complete(String result) {
        this.processed = true;
        this.result = result == null || result.length() <= 255 ? result : result.substring(0, 255);
    }

    public UUID getId() {
        return id;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEvent() {
        return event;
    }

    public String getAction() {
        return action;
    }

    public String getRepositoryFullName() {
        return repositoryFullName;
    }

    public boolean isProcessed() {
        return processed;
    }

    public String getResult() {
        return result;
    }

    public java.time.Instant getReceivedAt() {
        return receivedAt;
    }
}
