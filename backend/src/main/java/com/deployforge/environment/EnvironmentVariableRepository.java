package com.deployforge.environment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentVariableRepository extends JpaRepository<EnvironmentVariable, UUID> {

    List<EnvironmentVariable> findByEnvironmentIdOrderByKeyAsc(UUID environmentId);

    Optional<EnvironmentVariable> findByEnvironmentIdAndKey(UUID environmentId, String key);

    long countByEnvironmentId(UUID environmentId);

    void deleteByEnvironmentId(UUID environmentId);
}
