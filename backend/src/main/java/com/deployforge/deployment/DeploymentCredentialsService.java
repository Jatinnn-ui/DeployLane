package com.deployforge.deployment;

import com.deployforge.github.GitHubConnectionRepository;
import com.deployforge.project.Project;
import com.deployforge.security.EncryptionService;
import com.deployforge.workspace.WorkspaceMember;
import com.deployforge.workspace.WorkspaceMemberRepository;
import com.deployforge.workspace.WorkspaceRole;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds a GitHub token that can clone a project's repository.
 *
 * <p>A webhook triggered deployment has no interactive user, so the token cannot simply come from "the
 * current request". Resolution order:
 *
 * <ol>
 *   <li>the user who triggered the deployment
 *   <li>the user who imported the project
 *   <li>the highest privileged workspace member who still has a GitHub grant
 * </ol>
 *
 * <p>If none of those exist the clone proceeds anonymously, which succeeds for public repositories and
 * fails with a clear message for private ones.
 */
@Service
public class DeploymentCredentialsService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentCredentialsService.class);

    private final GitHubConnectionRepository connectionRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final EncryptionService encryptionService;

    public DeploymentCredentialsService(
            GitHubConnectionRepository connectionRepository,
            WorkspaceMemberRepository memberRepository,
            EncryptionService encryptionService) {
        this.connectionRepository = connectionRepository;
        this.memberRepository = memberRepository;
        this.encryptionService = encryptionService;
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveCloneToken(Project project, UUID triggeredBy) {
        return tokenFor(triggeredBy)
                .or(() -> tokenFor(project.getCreatedBy()))
                .or(() -> tokenFromWorkspace(project.getWorkspaceId()));
    }

    private Optional<String> tokenFor(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return connectionRepository
                .findByUserId(userId)
                .map(connection -> encryptionService.decrypt(connection.getAccessTokenEncrypted()));
    }

    private Optional<String> tokenFromWorkspace(UUID workspaceId) {
        return memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).stream()
                .sorted(Comparator.comparingInt((WorkspaceMember member) -> member.getRole().rank()).reversed())
                .filter(member -> member.getRole().rank() >= WorkspaceRole.DEVELOPER.rank())
                .map(member -> tokenFor(member.getUserId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .or(
                        () -> {
                            log.warn("clone_token_unavailable workspace={}", workspaceId);
                            return Optional.empty();
                        });
    }
}
