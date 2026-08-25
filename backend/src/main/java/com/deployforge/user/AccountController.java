package com.deployforge.user;

import com.deployforge.auth.RefreshTokenService;
import com.deployforge.common.error.Exceptions.NotFoundException;
import com.deployforge.github.GitHubService;
import com.deployforge.github.dto.GitHubDtos.GitHubConnectionStatus;
import com.deployforge.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own account. */
@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "The signed in user's profile and GitHub connection")
public class AccountController {

    private final UserRepository userRepository;
    private final GitHubService githubService;
    private final RefreshTokenService refreshTokenService;

    public AccountController(
            UserRepository userRepository,
            GitHubService githubService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.githubService = githubService;
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping
    public UserResponse profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return userRepository
                .findById(user.userId())
                .map(UserResponse::from)
                .orElseThrow(() -> NotFoundException.of("User", user.userId()));
    }

    @GetMapping("/github")
    public GitHubConnectionStatus github(@AuthenticationPrincipal AuthenticatedUser user) {
        return githubService.connectionStatus(user.userId());
    }

    @DeleteMapping("/github")
    @Transactional
    @Operation(
            summary = "Disconnect GitHub",
            description =
                    "Deletes the stored encrypted token and revokes every session, because without a GitHub grant "
                            + "the account cannot import or deploy anything.")
    public ResponseEntity<Void> disconnectGithub(@AuthenticationPrincipal AuthenticatedUser user) {
        githubService.disconnect(user.userId());
        refreshTokenService.revokeAll(user.userId());
        return ResponseEntity.noContent().build();
    }
}
