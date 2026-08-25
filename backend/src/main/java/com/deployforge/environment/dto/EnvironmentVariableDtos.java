package com.deployforge.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Environment variable payloads.
 *
 * <p>Note what is missing from every response type: the value. Reads return metadata only; the
 * plaintext exists in exactly two places - the encrypted column and the container's environment.
 */
public final class EnvironmentVariableDtos {

    private EnvironmentVariableDtos() {}

    public record EnvironmentVariableResponse(
            UUID id, UUID environmentId, String key, Instant createdAt, Instant updatedAt) {}

    public record VariableListResponse(
            UUID environmentId, List<EnvironmentVariableResponse> variables, int count) {}

    public record CreateVariableRequest(
            @NotBlank
                    @Size(max = 128)
                    @Pattern(
                            regexp = "^[A-Za-z_][A-Za-z0-9_]*$",
                            message = "must start with a letter or underscore and contain only letters, digits and underscores")
                    String key,
            @NotBlank @Size(max = 8192) String value) {}

    public record UpdateVariableRequest(@NotBlank @Size(max = 8192) String value) {}

    /**
     * Bulk paste of a {@code .env} style block.
     *
     * @param replaceExisting when true, variables absent from the payload are deleted
     */
    public record BulkVariablesRequest(
            @NotBlank @Size(max = 200_000) String content, boolean replaceExisting) {}

    public record BulkVariablesResponse(int created, int updated, int deleted, List<String> skipped) {}
}
