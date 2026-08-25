package com.deployforge.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Workspaces the user is a member of, newest first. Single query, no N+1. */
    @Query(
            """
            select w from Workspace w
            where w.id in (select m.workspaceId from WorkspaceMember m where m.userId = :userId)
            order by w.createdAt asc
            """)
    List<Workspace> findAllForMember(@Param("userId") UUID userId);
}
