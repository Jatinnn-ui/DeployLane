package com.deployforge.environment;

import com.deployforge.common.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * A single environment variable.
 *
 * <p>{@code encryptedValue} holds an AES-256-GCM ciphertext. There is deliberately no plaintext
 * column, no cached preview and no "last four characters" field - the value can only be recovered by
 * the deployment pipeline, which needs it to start a container.
 */
@Entity
@Table(
        name = "environment_variables",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "ux_env_var_key",
                        columnNames = {"environment_id", "env_key"}))
public class EnvironmentVariable extends AuditedEntity {

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "env_key", nullable = false)
    private String key;

    @Column(name = "encrypted_value", nullable = false, columnDefinition = "text")
    private String encryptedValue;

    protected EnvironmentVariable() {}

    public EnvironmentVariable(UUID environmentId, String key, String encryptedValue) {
        this.environmentId = environmentId;
        this.key = key;
        this.encryptedValue = encryptedValue;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public String getKey() {
        return key;
    }

    public String getEncryptedValue() {
        return encryptedValue;
    }

    public void setEncryptedValue(String encryptedValue) {
        this.encryptedValue = encryptedValue;
    }
}
