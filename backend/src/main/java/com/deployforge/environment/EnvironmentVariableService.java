package com.deployforge.environment;

import com.deployforge.common.error.Exceptions.ConflictException;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.common.util.Validators;
import com.deployforge.environment.dto.EnvironmentVariableDtos.BulkVariablesResponse;
import com.deployforge.environment.dto.EnvironmentVariableDtos.EnvironmentVariableResponse;
import com.deployforge.security.EncryptionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for encrypted environment variables.
 *
 * <p>Authorization happens in the controller layer via {@code ProjectAccessGuard}; this service is
 * also called by the deployment pipeline, which has no user context.
 *
 * <p>Nothing here ever logs a value. Keys are logged, values are not, not even at DEBUG.
 */
@Service
@Transactional(readOnly = true)
public class EnvironmentVariableService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentVariableService.class);
    private static final int MAX_VARIABLES_PER_ENVIRONMENT = 200;

    private final EnvironmentVariableRepository repository;
    private final EncryptionService encryptionService;

    public EnvironmentVariableService(
            EnvironmentVariableRepository repository, EncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    public List<EnvironmentVariableResponse> list(UUID environmentId) {
        return repository.findByEnvironmentIdOrderByKeyAsc(environmentId).stream()
                .map(this::toResponse)
                .toList();
    }

    public long count(UUID environmentId) {
        return repository.countByEnvironmentId(environmentId);
    }

    @Transactional
    public EnvironmentVariableResponse create(UUID environmentId, String key, String value) {
        String normalizedKey = Validators.requireEnvKey(key.trim());
        if (repository.findByEnvironmentIdAndKey(environmentId, normalizedKey).isPresent()) {
            throw new ConflictException(
                    normalizedKey + " already exists in this environment. Update it instead.");
        }
        if (repository.countByEnvironmentId(environmentId) >= MAX_VARIABLES_PER_ENVIRONMENT) {
            throw new ConflictException(
                    "An environment may hold at most " + MAX_VARIABLES_PER_ENVIRONMENT + " variables");
        }
        EnvironmentVariable variable =
                repository.save(
                        new EnvironmentVariable(
                                environmentId, normalizedKey, encryptionService.encrypt(value)));
        log.info("env_var_created environment={} key={}", environmentId, normalizedKey);
        return toResponse(variable);
    }

    @Transactional
    public EnvironmentVariableResponse update(UUID variableId, String value) {
        EnvironmentVariable variable =
                repository
                        .findById(variableId)
                        .orElseThrow(() -> NotFoundException.of("Environment variable", variableId));
        variable.setEncryptedValue(encryptionService.encrypt(value));
        log.info(
                "env_var_updated environment={} key={}", variable.getEnvironmentId(), variable.getKey());
        return toResponse(variable);
    }

    @Transactional
    public void delete(UUID variableId) {
        EnvironmentVariable variable =
                repository
                        .findById(variableId)
                        .orElseThrow(() -> NotFoundException.of("Environment variable", variableId));
        repository.delete(variable);
        log.info(
                "env_var_deleted environment={} key={}", variable.getEnvironmentId(), variable.getKey());
    }

    /** Used by import and by the bulk editor. Upserts without failing on duplicates. */
    @Transactional
    public void upsert(UUID environmentId, String key, String value) {
        String normalizedKey = Validators.requireEnvKey(key.trim());
        repository
                .findByEnvironmentIdAndKey(environmentId, normalizedKey)
                .ifPresentOrElse(
                        existing -> existing.setEncryptedValue(encryptionService.encrypt(value)),
                        () ->
                                repository.save(
                                        new EnvironmentVariable(
                                                environmentId,
                                                normalizedKey,
                                                encryptionService.encrypt(value))));
    }

    /**
     * Applies a pasted {@code .env} block.
     *
     * <p>Tolerant by design - comments, blank lines, {@code export } prefixes and quoted values are all
     * accepted, because this input is copied from a terminal or another platform. Malformed lines are
     * reported back instead of aborting the whole paste.
     */
    @Transactional
    public BulkVariablesResponse applyBulk(UUID environmentId, String content, boolean replaceExisting) {
        Map<String, String> parsed = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();

        int lineNumber = 0;
        for (String rawLine : content.split("\\R")) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                skipped.add("line " + lineNumber + ": no '=' found");
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            value = stripQuotes(value);

            if (!Validators.ENV_KEY.matcher(key).matches()) {
                skipped.add("line " + lineNumber + ": '" + sanitizeKeyForMessage(key) + "' is not a valid name");
                continue;
            }
            parsed.put(key, value);
        }

        Set<String> existingKeys = new LinkedHashSet<>();
        repository
                .findByEnvironmentIdOrderByKeyAsc(environmentId)
                .forEach(variable -> existingKeys.add(variable.getKey()));

        int created = 0;
        int updated = 0;
        for (Map.Entry<String, String> entry : parsed.entrySet()) {
            if (existingKeys.contains(entry.getKey())) {
                updated++;
            } else {
                created++;
            }
            upsert(environmentId, entry.getKey(), entry.getValue());
        }

        int deleted = 0;
        if (replaceExisting) {
            for (String existingKey : existingKeys) {
                if (!parsed.containsKey(existingKey)) {
                    repository
                            .findByEnvironmentIdAndKey(environmentId, existingKey)
                            .ifPresent(repository::delete);
                    deleted++;
                }
            }
        }

        log.info(
                "env_var_bulk_applied environment={} created={} updated={} deleted={} skipped={}",
                environmentId,
                created,
                updated,
                deleted,
                skipped.size());
        return new BulkVariablesResponse(created, updated, deleted, skipped);
    }

    /**
     * Decrypted variables for injection into a container.
     *
     * <p>Only the deployment pipeline calls this. The result must never be returned from an API or
     * written to a log.
     */
    public Map<String, String> resolveForRuntime(UUID environmentId) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (EnvironmentVariable variable : repository.findByEnvironmentIdOrderByKeyAsc(environmentId)) {
            resolved.put(variable.getKey(), encryptionService.decrypt(variable.getEncryptedValue()));
        }
        return resolved;
    }

    /** Variable names only - this is what may be shared with an AI provider. */
    public List<String> keyNames(UUID environmentId) {
        return repository.findByEnvironmentIdOrderByKeyAsc(environmentId).stream()
                .map(EnvironmentVariable::getKey)
                .toList();
    }

    @Transactional
    public void deleteAllForEnvironment(UUID environmentId) {
        repository.deleteByEnvironmentId(environmentId);
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String sanitizeKeyForMessage(String key) {
        String cleaned = key.replaceAll("[^A-Za-z0-9_\\-]", "");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private EnvironmentVariableResponse toResponse(EnvironmentVariable variable) {
        return new EnvironmentVariableResponse(
                variable.getId(),
                variable.getEnvironmentId(),
                variable.getKey(),
                variable.getCreatedAt(),
                variable.getUpdatedAt());
    }
}
