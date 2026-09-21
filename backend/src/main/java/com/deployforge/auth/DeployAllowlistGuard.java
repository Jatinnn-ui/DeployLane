package com.deployforge.auth;

import com.deployforge.common.error.Exceptions.AccessDeniedException;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.user.User;
import com.deployforge.user.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gates who may deploy and import projects.
 *
 * <p>The site is intentionally public: anyone can sign in with GitHub and browse. Actually running a
 * deployment, however, executes the user's code on this host, so it is restricted to an allowlist of
 * GitHub usernames configured via {@code DEPLOY_ALLOWLIST}. When the allowlist is empty the platform
 * behaves as before and any signed-in user may deploy.
 */
@Component
public class DeployAllowlistGuard {

    private static final Logger log = LoggerFactory.getLogger(DeployAllowlistGuard.class);

    private final DeployForgeProperties properties;
    private final UserRepository userRepository;

    public DeployAllowlistGuard(DeployForgeProperties properties, UserRepository userRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
    }

    /**
     * Throws {@link AccessDeniedException} (HTTP 403) when the given user is not permitted to deploy.
     * A no-op when the allowlist is empty.
     */
    public void requireCanDeploy(UUID userId) {
        if (!properties.security().deployRestricted()) {
            return;
        }
        String username =
                userRepository.findById(userId).map(User::getGithubUsername).orElse(null);
        if (!properties.security().isDeployAllowed(username)) {
            log.warn("deploy_denied_not_allowlisted user={} github={}", userId, username);
            throw new AccessDeniedException(
                    "Deploying is limited to approved accounts on this instance. "
                            + "You can sign in and explore, but ask the owner for deploy access.");
        }
    }
}
