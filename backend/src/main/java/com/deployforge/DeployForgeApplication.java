package com.deployforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DeployLane - modern deployment and project management platform.
 *
 * <p>Single node architecture: this application talks to the local Docker engine to build images
 * and run application containers. Everything is designed behind interfaces
 * ({@code DeploymentRuntime}, {@code DomainRoutingService}, {@code AiProvider}) so a remote /
 * Kubernetes runtime can be added later without touching the domain logic.
 */
/*
 * UserDetailsServiceAutoConfiguration is excluded because authentication is entirely JWT based:
 * JwtAuthenticationFilter populates the SecurityContext itself, so there is no UserDetailsService,
 * AuthenticationProvider or AuthenticationManager bean for that autoconfiguration to back off from.
 * Left enabled it creates an in-memory "user" with a random password and logs it at WARN on every
 * boot - a credential nothing can authenticate with, and a warning that trains operators to ignore
 * warnings.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class DeployForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeployForgeApplication.class, args);
    }
}
