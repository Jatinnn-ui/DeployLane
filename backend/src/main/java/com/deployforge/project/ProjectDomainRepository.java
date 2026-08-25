package com.deployforge.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectDomainRepository extends JpaRepository<ProjectDomain, UUID> {

    List<ProjectDomain> findByProjectId(UUID projectId);

    Optional<ProjectDomain> findFirstByEnvironmentIdAndKind(
            UUID environmentId, ProjectDomain.DomainKind kind);

    Optional<ProjectDomain> findByHostname(String hostname);

    void deleteByProjectId(UUID projectId);
}
