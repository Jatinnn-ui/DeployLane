package com.deployforge.webhook;

import com.deployforge.common.util.JsonCodec;
import com.deployforge.common.util.Validators;
import com.deployforge.deployment.Deployment;
import com.deployforge.deployment.DeploymentService;
import com.deployforge.deployment.DeploymentTriggerType;
import com.deployforge.environment.Environment;
import com.deployforge.environment.EnvironmentRepository;
import com.deployforge.project.Project;
import com.deployforge.project.ProjectRepository;
import com.deployforge.project.ProjectStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a verified GitHub webhook into deployments.
 *
 * <p>Idempotency is the hard part, not parsing. GitHub retries deliveries, and a retry must not deploy
 * the same commit twice, so the delivery id is inserted first and a unique constraint violation is
 * treated as "already handled". That works across application instances, unlike an in-memory cache.
 *
 * <p>One repository may be imported into several workspaces; each matching project with auto deploy
 * enabled for the pushed branch gets its own deployment.
 */
@Service
public class GitHubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookService.class);
    private static final String BRANCH_REF_PREFIX = "refs/heads/";

    private final WebhookDeliveryRepository deliveryRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final DeploymentService deploymentService;
    private final JsonCodec json;

    public GitHubWebhookService(
            WebhookDeliveryRepository deliveryRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            DeploymentService deploymentService,
            JsonCodec json) {
        this.deliveryRepository = deliveryRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.deploymentService = deploymentService;
        this.json = json;
    }

    /**
     * @return a short, human readable outcome, echoed back to GitHub for its delivery log
     */
    public WebhookResult handle(String deliveryId, String event, String rawBody) {
        JsonNode payload;
        try {
            payload = json.mapper().readTree(rawBody);
        } catch (Exception e) {
            log.warn("webhook_unparseable delivery={} event={}", deliveryId, event);
            return new WebhookResult(false, "payload could not be parsed", List.of());
        }

        String action = text(payload, "action");
        String repositoryFullName = text(payload.path("repository"), "full_name");

        Optional<WebhookDelivery> reserved = reserveDelivery(deliveryId, event, action, repositoryFullName);
        if (reserved.isEmpty()) {
            log.info("webhook_duplicate_ignored delivery={} event={}", deliveryId, event);
            return new WebhookResult(true, "duplicate delivery ignored", List.of());
        }

        WebhookResult result =
                switch (event) {
                    case "push" -> handlePush(payload);
                    case "pull_request" -> handlePullRequest(payload, action);
                    case "ping" -> new WebhookResult(true, "pong", List.of());
                    default -> new WebhookResult(true, "event ignored: " + event, List.of());
                };

        completeDelivery(reserved.get(), result.message());
        return result;
    }

    // ------------------------------------------------------------------ push

    private WebhookResult handlePush(JsonNode payload) {
        if (payload.path("deleted").asBoolean(false)) {
            return new WebhookResult(true, "branch deleted, nothing to deploy", List.of());
        }
        String ref = text(payload, "ref");
        if (ref == null || !ref.startsWith(BRANCH_REF_PREFIX)) {
            return new WebhookResult(true, "not a branch push", List.of());
        }
        String branch = ref.substring(BRANCH_REF_PREFIX.length());

        String owner = text(payload.path("repository").path("owner"), "name");
        if (owner == null) {
            owner = text(payload.path("repository").path("owner"), "login");
        }
        String repository = text(payload.path("repository"), "name");
        if (owner == null || repository == null) {
            return new WebhookResult(false, "repository owner or name missing", List.of());
        }

        JsonNode headCommit = payload.path("head_commit");
        String commitSha = text(headCommit, "id");
        String commitMessage = firstLine(text(headCommit, "message"));
        String commitAuthor = text(headCommit.path("author"), "name");

        List<Project> projects =
                projectRepository.findByRepositoryOwnerIgnoreCaseAndRepositoryNameIgnoreCase(
                        owner, repository);
        if (projects.isEmpty()) {
            return new WebhookResult(
                    true, "no DeployLane project tracks " + owner + "/" + repository, List.of());
        }

        List<String> triggered = new ArrayList<>();
        for (Project project : projects) {
            if (project.getStatus() != ProjectStatus.ACTIVE) {
                continue;
            }
            List<Environment> environments =
                    environmentRepository.findByProjectIdAndBranchAndAutoDeployEnabledTrue(
                            project.getId(), branch);
            for (Environment environment : environments) {
                try {
                    Deployment deployment =
                            deploymentService.createFromWebhook(
                                    project,
                                    environment,
                                    Validators.requireBranch(branch),
                                    commitSha,
                                    commitMessage,
                                    commitAuthor,
                                    DeploymentTriggerType.GIT_PUSH);
                    triggered.add(project.getSlug() + "#" + deployment.getDeploymentNumber());
                    log.info(
                            "auto_deploy_triggered project={} environment={} branch={} deployment={}",
                            project.getId(),
                            environment.getId(),
                            branch,
                            deployment.getId());
                } catch (RuntimeException e) {
                    // One misconfigured project must not stop the others from deploying.
                    log.warn(
                            "auto_deploy_failed project={} branch={} reason={}",
                            project.getId(),
                            branch,
                            e.getMessage());
                }
            }
        }

        if (triggered.isEmpty()) {
            return new WebhookResult(
                    true,
                    "no environment has auto deploy enabled for branch " + branch,
                    List.of());
        }
        return new WebhookResult(true, "queued " + triggered.size() + " deployment(s)", triggered);
    }

    // ------------------------------------------------------------------ pull request

    /**
     * Pull request handling.
     *
     * <p>Full preview environments (create on open, destroy on close) are a later phase. What works today
     * is honest and useful: if a PREVIEW environment already exists for the PR's head branch, the push is
     * deployed to it.
     */
    private WebhookResult handlePullRequest(JsonNode payload, String action) {
        if (action == null
                || !(action.equals("opened") || action.equals("synchronize") || action.equals("reopened"))) {
            return new WebhookResult(true, "pull_request action ignored: " + action, List.of());
        }
        JsonNode pullRequest = payload.path("pull_request");
        String branch = text(pullRequest.path("head"), "ref");
        String owner = text(payload.path("repository").path("owner"), "login");
        String repository = text(payload.path("repository"), "name");
        String commitSha = text(pullRequest.path("head"), "sha");
        int number = payload.path("number").asInt(0);

        if (branch == null || owner == null || repository == null) {
            return new WebhookResult(false, "pull request payload incomplete", List.of());
        }

        List<Project> projects =
                projectRepository.findByRepositoryOwnerIgnoreCaseAndRepositoryNameIgnoreCase(
                        owner, repository);
        List<String> triggered = new ArrayList<>();
        for (Project project : projects) {
            if (project.getStatus() != ProjectStatus.ACTIVE) {
                continue;
            }
            for (Environment environment :
                    environmentRepository.findByProjectIdAndBranchAndAutoDeployEnabledTrue(
                            project.getId(), branch)) {
                if (environment.getType() != com.deployforge.environment.EnvironmentType.PREVIEW) {
                    continue;
                }
                try {
                    Deployment deployment =
                            deploymentService.createFromWebhook(
                                    project,
                                    environment,
                                    Validators.requireBranch(branch),
                                    commitSha,
                                    "PR #" + number,
                                    null,
                                    DeploymentTriggerType.PULL_REQUEST);
                    triggered.add(project.getSlug() + "#" + deployment.getDeploymentNumber());
                } catch (RuntimeException e) {
                    log.warn(
                            "preview_deploy_failed project={} branch={} reason={}",
                            project.getId(),
                            branch,
                            e.getMessage());
                }
            }
        }
        return triggered.isEmpty()
                ? new WebhookResult(
                        true, "no preview environment configured for branch " + branch, List.of())
                : new WebhookResult(true, "queued " + triggered.size() + " preview deployment(s)", triggered);
    }

    // ------------------------------------------------------------------ idempotency

    /**
     * Claims a delivery id.
     *
     * @return empty when this delivery was already recorded, meaning it must not be processed again
     */
    @Transactional
    public Optional<WebhookDelivery> reserveDelivery(
            String deliveryId, String event, String action, String repositoryFullName) {
        if (deliveryId == null || deliveryId.isBlank()) {
            // No delivery id (manual curl during setup): process it, but do not record anything.
            return Optional.of(new WebhookDelivery("adhoc-" + System.nanoTime(), event, action, repositoryFullName));
        }
        if (deliveryRepository.existsByDeliveryId(deliveryId)) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    deliveryRepository.saveAndFlush(
                            new WebhookDelivery(deliveryId, event, action, repositoryFullName)));
        } catch (DataIntegrityViolationException e) {
            // Two instances received the retry at the same time; the loser stops here.
            return Optional.empty();
        }
    }

    @Transactional
    public void completeDelivery(WebhookDelivery delivery, String result) {
        if (delivery.getId() == null) {
            return;
        }
        deliveryRepository
                .findById(delivery.getId())
                .ifPresent(
                        managed -> {
                            managed.complete(result);
                            deliveryRepository.save(managed);
                        });
    }

    // ------------------------------------------------------------------ helpers

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private String firstLine(String value) {
        if (value == null) {
            return null;
        }
        return value.lines().findFirst().orElse(value);
    }

    public record WebhookResult(boolean accepted, String message, List<String> deployments) {}
}
