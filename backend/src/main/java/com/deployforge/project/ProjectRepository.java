package com.deployforge.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Page<Project> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    List<Project> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<Project> findByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    boolean existsByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    long countByWorkspaceId(UUID workspaceId);

    /** Projects across every workspace the user belongs to, newest first. */
    @Query(
            """
            select p from Project p
            where p.workspaceId in (select m.workspaceId from WorkspaceMember m where m.userId = :userId)
            order by p.createdAt desc
            """)
    Page<Project> findAllVisibleTo(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Candidates for an incoming webhook. A repository can legitimately be imported into several
     * workspaces, so this returns a list.
     */
    List<Project> findByRepositoryOwnerIgnoreCaseAndRepositoryNameIgnoreCase(
            String repositoryOwner, String repositoryName);
}
