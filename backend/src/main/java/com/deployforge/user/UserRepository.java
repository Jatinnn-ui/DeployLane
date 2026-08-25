package com.deployforge.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByGithubUserId(Long githubUserId);

    Optional<User> findByGithubUsernameIgnoreCase(String githubUsername);

    List<User> findByIdIn(List<UUID> ids);
}
