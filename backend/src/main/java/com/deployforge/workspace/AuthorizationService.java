package com.deployforge.workspace;

import com.deployforge.common.error.Exceptions.AccessDeniedException;
import com.deployforge.common.error.Exceptions.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place where "may this user do this?" is answered.
 *
 * <p>Two deliberate choices:
 *
 * <ul>
 *   <li>Membership is read from the database on every call. Roles are never cached in the JWT, so
 *       revoking access takes effect immediately.
 *   <li>A non-member gets {@code 404}, not {@code 403}. Telling a stranger "this workspace exists but
 *       you cannot see it" leaks information; members who merely lack a permission do get {@code 403}
 *       because that is actionable for them.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private final WorkspaceMemberRepository memberRepository;

    public AuthorizationService(WorkspaceMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Optional<WorkspaceRole> roleOf(UUID workspaceId, UUID userId) {
        return memberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMember::getRole);
    }

    /** @throws NotFoundException when the user is not a member of the workspace */
    public WorkspaceRole requireMembership(UUID workspaceId, UUID userId) {
        return roleOf(workspaceId, userId)
                .orElseThrow(() -> NotFoundException.of("Workspace", workspaceId));
    }

    /**
     * @throws NotFoundException when the user is not a member
     * @throws AccessDeniedException when the member's role does not grant {@code permission}
     */
    public WorkspaceRole require(UUID workspaceId, UUID userId, Permission permission) {
        WorkspaceRole role = requireMembership(workspaceId, userId);
        if (!role.can(permission)) {
            log.warn(
                    "authorization_denied workspace={} user={} role={} permission={}",
                    workspaceId,
                    userId,
                    role,
                    permission);
            throw new AccessDeniedException(
                    "Your role (" + role + ") does not allow " + humanize(permission));
        }
        return role;
    }

    public boolean can(UUID workspaceId, UUID userId, Permission permission) {
        return roleOf(workspaceId, userId).map(role -> role.can(permission)).orElse(false);
    }

    private String humanize(Permission permission) {
        return permission.name().toLowerCase().replace('_', ' ');
    }
}
