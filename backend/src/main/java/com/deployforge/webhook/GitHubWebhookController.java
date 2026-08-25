package com.deployforge.webhook;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.security.RateLimitService;
import com.deployforge.webhook.GitHubWebhookService.WebhookResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GitHub webhook ingest.
 *
 * <p>Public by necessity, so it is defended by three things instead of a session: HMAC signature
 * verification, delivery-id idempotency and a rate limit. The body is taken as a raw {@code String}
 * because the signature is computed over the exact bytes GitHub sent - binding to a DTO and
 * re-serialising would invalidate it.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Automatic deployments triggered by GitHub events")
@SecurityRequirements
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubWebhookVerifier verifier;
    private final GitHubWebhookService webhookService;
    private final RateLimitService rateLimitService;
    private final DeployForgeProperties properties;

    public GitHubWebhookController(
            GitHubWebhookVerifier verifier,
            GitHubWebhookService webhookService,
            RateLimitService rateLimitService,
            DeployForgeProperties properties) {
        this.verifier = verifier;
        this.webhookService = webhookService;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    @PostMapping(value = "/github", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "GitHub webhook endpoint",
            description =
                    "Verifies X-Hub-Signature-256, deduplicates by X-GitHub-Delivery and queues deployments for "
                            + "environments with auto deploy enabled on the pushed branch.")
    public ResponseEntity<WebhookResult> receive(
            @RequestHeader(name = "X-GitHub-Event", required = false) String event,
            @RequestHeader(name = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawBody) {

        rateLimitService.checkPerMinute(
                "webhook", "github", properties.rateLimit().webhookPerMinute());
        verifier.verify(rawBody, signature);

        if (event == null || event.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new WebhookResult(false, "missing X-GitHub-Event header", java.util.List.of()));
        }

        log.info("webhook_received event={} delivery={}", event, deliveryId);
        WebhookResult result = webhookService.handle(deliveryId, event, rawBody);
        return result.accepted()
                ? ResponseEntity.accepted().body(result)
                : ResponseEntity.badRequest().body(result);
    }
}
