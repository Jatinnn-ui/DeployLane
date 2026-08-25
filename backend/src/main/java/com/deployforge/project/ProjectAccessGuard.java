package com.deployforge.project;

import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.workspace.AuthorizationService;
import com.deployforge.workspace.Permission;
import com.deployforge.workspace.WorkspaceRole;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a project and authorizes the caller in one step.
 *
 * <p>Every project-scoped service and controller goes through this, which is what makes it
 * impossible to forget an authorization check while adding an endpoint. Non-members get a 404 for the
 * project itself so the existence of other tenants' projects is not observable.
 */
@Service
@Transactional(readOnly = true)
public class ProjectAccessGuard {

    private final ProjectRepository projectRepository;
    private final AuthorizationService authorization;

    public ProjectAccessGuard(ProjectRepository projectRepository, AuthorizationService authorization) {
        this.projectRepository = projectRepository;
        this.authorization = authorization;
    }

    public Project require(UUID projectId, UUID userId, Permission permission) {
        Project project = loadVisible(projectId, userId);
        authorization.require(project.getWorkspaceId(), userId, permission);
        return project;
    }

    public Project requireVisible(UUID projectId, UUID userId) {
        return loadVisible(projectId, userId);
    }

    /**
     * Loads a project without a user context.
     *
     * <p>For background work only - webhook handling, scheduled jobs and AI analysis triggered by the
     * pipeline. There is no caller to authorize in those paths, so this method is named to make its use
     * obvious in review.
     */
    public Project requireProjectForInternalUse(UUID projectId) {
        return projectRepository
                .findById(projectId)
                .orElseThrow(() -> NotFoundException.of("Project", projectId));
    }

    public WorkspaceRole roleFor(Project project, UUID userId) {
        return authorization
                .roleOf(project.getWorkspaceId(), userId)
                .orElseThrow(() -> NotFoundException.of("Project", project.getId()));
    }

    private Project loadVisible(UUID projectId, UUID userId) {
        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() -> NotFoundException.of("Project", projectId));
        if (authorization.roleOf(project.getWorkspaceId(), userId).isEmpty()) {
            throw NotFoundException.of("Project", projectId);
        }
        return project;
    }
}
