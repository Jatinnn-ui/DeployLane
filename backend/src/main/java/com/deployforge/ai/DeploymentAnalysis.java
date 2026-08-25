package com.deployforge.ai;

import com.deployforge.ai.AiModels.Severity;
import com.deployforge.common.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Stored AI analysis of a failed deployment.
 *
 * <p>One row per deployment, which also makes it the cache: analysis is expensive and a deployment's
 * failure never changes, so it is computed once unless a re-analysis is explicitly requested.
 *
 * <p>{@code rawProviderResponse} is kept for debugging model behaviour, redacted like everything else.
 */
@Entity
@Table(name = "deployment_analyses")
public class DeploymentAnalysis extends AuditedEntity {

    @Column(name = "deployment_id", nullable = false, unique = true)
    private UUID deploymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AnalysisStatus status;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    /** JSON array of strings. */
    @Column(name = "evidence", columnDefinition = "text")
    private String evidence;

    /** JSON array of {title, description, command}. */
    @Column(name = "suggested_fixes", columnDefinition = "text")
    private String suggestedFixes;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16)
    private Severity severity;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "raw_provider_response", columnDefinition = "text")
    private String rawProviderResponse;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    protected DeploymentAnalysis() {}

    public DeploymentAnalysis(UUID deploymentId) {
        this.deploymentId = deploymentId;
        this.status = AnalysisStatus.PENDING;
    }

    public UUID getDeploymentId() {
        return deploymentId;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getSuggestedFixes() {
        return suggestedFixes;
    }

    public void setSuggestedFixes(String suggestedFixes) {
        this.suggestedFixes = suggestedFixes;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRawProviderResponse() {
        return rawProviderResponse;
    }

    public void setRawProviderResponse(String rawProviderResponse) {
        this.rawProviderResponse = rawProviderResponse;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage == null || errorMessage.length() <= 1000
                ? errorMessage
                : errorMessage.substring(0, 1000);
    }

    public enum AnalysisStatus {
        /** Queued or in flight. */
        PENDING,
        /** A validated analysis is available. */
        COMPLETED,
        /** No provider could produce a usable analysis; the UI shows a plain "unavailable" state. */
        UNAVAILABLE
    }
}
