package com.deployforge.workspace;

import com.deployforge.common.error.Exceptions.BadRequestException;
import com.deployforge.common.error.Exceptions.ConflictException;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.common.util.Slugs;
import com.deployforge.user.User;
import com.deployforge.user.UserRepository;
import com.deployforge.user.UserResponse;
import com.deployforge.workspace.dto.WorkspaceDtos.AddMemberRequest;
import com.deployforge.workspace.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.deployforge.workspace.dto.WorkspaceDtos.UpdateWorkspaceRequest;
import com.deployforge.workspace.dto.WorkspaceDtos.WorkspaceMemberResponse;
import com.deployforge.workspace.dto.WorkspaceDtos.WorkspaceResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Workspace lifecycle and membership management. */
@Service
@Transactional(readOnly = true)
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);
    private static final int MAX_SLUG_ATTEMPTS = 50;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorization;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            UserRepository userRepository,
            AuthorizationService authorization) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.authorization = authorization;
    }

    /**
     * Creates the personal workspace that every new user lands in, so the dashboard is never empty
     * and project import works immediately after the first login.
     */
    @Transactional
    public Workspace provisionPersonalWorkspace(User user) {
        List<Workspace> existing = workspaceRepository.findAllForMember(user.getId());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String name = user.displayName() + "'s workspace";
        Workspace workspace =
                workspaceRepository.save(
                        new Workspace(name, uniqueSlug(user.getGithubUsername()), user.getId()));
        memberRepository.save(new WorkspaceMember(workspace.getId(), user.getId(), WorkspaceRole.OWNER));
        log.info("workspace_provisioned workspace={} owner={}", workspace.getId(), user.getId());
        return workspace;
    }

    @Transactional
    public WorkspaceResponse create(UUID userId, CreateWorkspaceRequest request) {
        String requestedSlug =
                StringUtils.hasText(request.slug()) ? request.slug() : Slugs.slugify(request.name());
        if (!Slugs.isValid(requestedSlug)) {
            throw new BadRequestException(
                    "Workspace name must contain at least two alphanumeric characters");
        }
        if (workspaceRepository.existsBySlug(requestedSlug)) {
            throw new ConflictException("A workspace with the slug '" + requestedSlug + "' already exists");
        }
        Workspace workspace =
                workspaceRepository.save(new Workspace(request.name().trim(), requestedSlug, userId));
        memberRepository.save(new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.OWNER));
        log.info("workspace_created workspace={} owner={}", workspace.getId(), userId);
        return toResponse(workspace, WorkspaceRole.OWNER, 1);
    }

    public List<WorkspaceResponse> listForUser(UUID userId) {
        List<Workspace> workspaces = workspaceRepository.findAllForMember(userId);
        if (workspaces.isEmpty()) {
            return List.of();
        }
        Map<UUID, WorkspaceRole> roles =
                memberRepository.findByUserId(userId).stream()
                        .collect(
                                Collectors.toMap(
                                        WorkspaceMember::getWorkspaceId,
                                        WorkspaceMember::getRole,
                                        (a, b) -> a));
        Map<UUID, Long> memberCounts = new HashMap<>();
        for (Workspace workspace : workspaces) {
            memberCounts.put(
                    workspace.getId(),
                    (long) memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspace.getId()).size());
        }
        return workspaces.stream()
                .map(
                        workspace ->
                                toResponse(
                                        workspace,
                                        roles.getOrDefault(workspace.getId(), WorkspaceRole.VIEWER),
                                        memberCounts.getOrDefault(workspace.getId(), 1L)))
                .toList();
    }

    public WorkspaceResponse get(UUID workspaceId, UUID userId) {
        WorkspaceRole role = authorization.requireMembership(workspaceId, userId);
        Workspace workspace = requireWorkspace(workspaceId);
        return toResponse(
                workspace,
                role,
                memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).size());
    }

    public WorkspaceResponse getBySlug(String slug, UUID userId) {
        Workspace workspace =
                workspaceRepository.findBySlug(slug).orElseThrow(() -> NotFoundException.of("Workspace", slug));
        return get(workspace.getId(), userId);
    }

    @Transactional
    public WorkspaceResponse update(UUID workspaceId, UUID userId, UpdateWorkspaceRequest request) {
        authorization.require(workspaceId, userId, Permission.MANAGE_WORKSPACE);
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.setName(request.name().trim());
        return toResponse(
                workspace,
                WorkspaceRole.OWNER,
                memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId).size());
    }

    public List<WorkspaceMemberResponse> listMembers(UUID workspaceId, UUID userId) {
        authorization.requireMembership(workspaceId, userId);
        List<WorkspaceMember> members = memberRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId);
        Map<UUID, User> users =
                userRepository.findByIdIn(members.stream().map(WorkspaceMember::getUserId).toList()).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<WorkspaceMemberResponse> result = new ArrayList<>(members.size());
        for (WorkspaceMember member : members) {
            User user = users.get(member.getUserId());
            if (user == null) {
                continue;
            }
            result.add(
                    new WorkspaceMemberResponse(
                            member.getId(),
                            UserResponse.publicProfile(user),
                            member.getRole(),
                            member.getCreatedAt()));
        }
        return result;
    }

    /**
     * Adds an existing DeployLane user to the workspace.
     *
     * <p>The invitee must have signed in at least once - DeployLane does not create shadow accounts
     * from a GitHub handle, because that would let anyone probe which GitHub users exist here.
     */
    @Transactional
    public WorkspaceMemberResponse addMember(UUID workspaceId, UUID actorId, AddMemberRequest request) {
        authorization.require(workspaceId, actorId, Permission.MANAGE_MEMBERS);
        if (request.role() == WorkspaceRole.OWNER) {
            throw new BadRequestException(
                    "A workspace has exactly one owner. Transfer ownership instead of adding a second one.");
        }
        User invitee =
                userRepository
                        .findByGithubUsernameIgnoreCase(request.githubUsername().trim())
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "No DeployLane user with the GitHub handle '"
                                                        + request.githubUsername()
                                                        + "'. They need to sign in once before they can be added."));

        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, invitee.getId())) {
            throw new ConflictException(invitee.getGithubUsername() + " is already a member");
        }
        WorkspaceMember member =
                memberRepository.save(new WorkspaceMember(workspaceId, invitee.getId(), request.role()));
        log.info(
                "workspace_member_added workspace={} member={} role={} by={}",
                workspaceId,
                invitee.getId(),
                request.role(),
                actorId);
        return new WorkspaceMemberResponse(
                member.getId(), UserResponse.publicProfile(invitee), member.getRole(), member.getCreatedAt());
    }

    @Transactional
    public WorkspaceMemberResponse updateMemberRole(
            UUID workspaceId, UUID memberId, UUID actorId, WorkspaceRole newRole) {
        WorkspaceRole actorRole = authorization.require(workspaceId, actorId, Permission.MANAGE_MEMBERS);
        WorkspaceMember member =
                memberRepository
                        .findById(memberId)
                        .filter(m -> m.getWorkspaceId().equals(workspaceId))
                        .orElseThrow(() -> NotFoundException.of("Workspace member", memberId));

        if (newRole == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.OWNER) {
            throw new BadRequestException("Ownership changes are not supported yet");
        }
        // No privilege escalation: you cannot grant a role at or above your own rank.
        if (newRole.rank() >= actorRole.rank() && actorRole != WorkspaceRole.OWNER) {
            throw new BadRequestException("You cannot grant a role equal to or above your own");
        }
        member.setRole(newRole);
        User user =
                userRepository
                        .findById(member.getUserId())
                        .orElseThrow(() -> NotFoundException.of("User", member.getUserId()));
        log.info("workspace_member_role_changed workspace={} member={} role={} by={}", workspaceId, memberId, newRole, actorId);
        return new WorkspaceMemberResponse(
                member.getId(), UserResponse.publicProfile(user), newRole, member.getCreatedAt());
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID memberId, UUID actorId) {
        authorization.require(workspaceId, actorId, Permission.MANAGE_MEMBERS);
        WorkspaceMember member =
                memberRepository
                        .findById(memberId)
                        .filter(m -> m.getWorkspaceId().equals(workspaceId))
                        .orElseThrow(() -> NotFoundException.of("Workspace member", memberId));
        if (member.getRole() == WorkspaceRole.OWNER) {
            throw new BadRequestException("The workspace owner cannot be removed");
        }
        memberRepository.delete(member);
        log.info("workspace_member_removed workspace={} member={} by={}", workspaceId, memberId, actorId);
    }

    public Workspace requireWorkspace(UUID workspaceId) {
        return workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> NotFoundException.of("Workspace", workspaceId));
    }

    private WorkspaceResponse toResponse(Workspace workspace, WorkspaceRole role, long memberCount) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getOwnerId(),
                role,
                role.permissions(),
                memberCount,
                workspace.getCreatedAt());
    }

    private String uniqueSlug(String base) {
        String slug = Slugs.slugify(base);
        if (slug.isEmpty()) {
            slug = "workspace";
        }
        String candidate = slug;
        for (int attempt = 2; attempt < MAX_SLUG_ATTEMPTS; attempt++) {
            if (!workspaceRepository.existsBySlug(candidate)) {
                return candidate;
            }
            candidate = slug + "-" + attempt;
        }
        return slug + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
