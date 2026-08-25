package com.deployforge.auth;

import com.deployforge.github.GitHubService;
import com.deployforge.github.api.GitHubApiModels.GitHubAccessTokenResponse;
import com.deployforge.github.api.GitHubApiModels.GitHubUser;
import com.deployforge.user.User;
import com.deployforge.user.UserRepository;
import com.deployforge.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists everything a successful GitHub login implies, in one transaction: the user identity, the
 * encrypted OAuth grant and the personal workspace.
 *
 * <p>Kept as its own bean on purpose - a {@code @Transactional} method called from inside the same
 * class would bypass the proxy and silently run without a transaction.
 */
@Service
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final GitHubService githubService;
    private final WorkspaceService workspaceService;

    public UserProvisioningService(
            UserRepository userRepository,
            GitHubService githubService,
            WorkspaceService workspaceService) {
        this.userRepository = userRepository;
        this.githubService = githubService;
        this.workspaceService = workspaceService;
    }

    @Transactional
    public User provision(GitHubUser profile, String email, GitHubAccessTokenResponse tokenResponse) {
        User user =
                userRepository
                        .findByGithubUserId(profile.id())
                        .map(
                                existing -> {
                                    // GitHub handles can be renamed; the immutable account id is the identity.
                                    existing.setGithubUsername(profile.login());
                                    existing.setName(profile.name());
                                    existing.setAvatarUrl(profile.avatarUrl());
                                    if (email != null) {
                                        existing.setEmail(email);
                                    }
                                    return existing;
                                })
                        .orElseGet(
                                () ->
                                        new User(
                                                profile.id(),
                                                profile.login(),
                                                profile.name(),
                                                email,
                                                profile.avatarUrl()));
        user = userRepository.save(user);

        githubService.saveConnection(
                user.getId(),
                profile.id(),
                tokenResponse.accessToken(),
                tokenResponse.tokenType(),
                tokenResponse.scope());

        workspaceService.provisionPersonalWorkspace(user);
        githubService.evictCatalogue(user.getId());
        return user;
    }
}
