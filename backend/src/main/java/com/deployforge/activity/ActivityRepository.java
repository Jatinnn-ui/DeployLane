package com.deployforge.activity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<ActivityEvent, UUID> {

    Page<ActivityEvent> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);

    Page<ActivityEvent> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    @Query(
            """
            select a from ActivityEvent a
            where a.workspaceId in :workspaceIds
            order by a.createdAt desc
            """)
    Page<ActivityEvent> findForWorkspaces(
            @Param("workspaceIds") List<UUID> workspaceIds, Pageable pageable);

    @Modifying
    @Query("delete from ActivityEvent a where a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
