package com.deployforge.health;

import com.deployforge.health.PlatformHealthService.PlatformHealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public platform health.
 *
 * <p>Used by the dashboard to show the "backend connected" state and by operators to see at a glance
 * whether the platform can currently deploy anything.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Platform connectivity and dependency health")
@SecurityRequirements
public class HealthController {

    private final PlatformHealthService healthService;

    public HealthController(PlatformHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    @Operation(
            summary = "Platform health",
            description =
                    "Reports the state of PostgreSQL, Redis, the container runtime and build storage. "
                            + "Returns 200 when every dependency is up and 503 when the platform is degraded, "
                            + "so a load balancer can use it directly.")
    public ResponseEntity<PlatformHealthResponse> health() {
        PlatformHealthResponse response = healthService.check();
        return "UP".equals(response.status())
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(503).body(response);
    }
}
