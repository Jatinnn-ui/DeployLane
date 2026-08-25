package com.deployforge.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<WorkspaceMember> findByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    List<WorkspaceMember> findByUserId(UUID userId);

    long countByWorkspaceIdAndRole(UUID workspaceId, WorkspaceRole role);

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
