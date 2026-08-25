package com.deployforge.runtime.docker;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Labels stamped on every image and container DeployForge creates.
 *
 * <p>This is what makes cleanup safe. The scheduled reaper only ever touches resources carrying
 * {@code deployforge.managed=true}; it never runs {@code docker system prune}, because the Docker
 * engine it talks to also runs the user's own unrelated containers.
 */
public final class DockerLabels {

    public static final String MANAGED = "deployforge.managed";
    public static final String PROJECT_ID = "deployforge.projectId";
    public static final String PROJECT_SLUG = "deployforge.projectSlug";
    public static final String ENVIRONMENT_ID = "deployforge.environmentId";
    public static final String DEPLOYMENT_ID = "deployforge.deploymentId";
    public static final String DEPLOYMENT_NUMBER = "deployforge.deploymentNumber";
    public static final String COMMIT_SHA = "deployforge.commitSha";
    public static final String CREATED_AT = "deployforge.createdAt";

    private DockerLabels() {}

    public static Map<String, String> forDeployment(
            UUID projectId,
            String projectSlug,
            UUID environmentId,
            UUID deploymentId,
            int deploymentNumber,
            String commitSha) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(MANAGED, "true");
        labels.put(PROJECT_ID, String.valueOf(projectId));
        labels.put(PROJECT_SLUG, projectSlug == null ? "" : projectSlug);
        labels.put(ENVIRONMENT_ID, String.valueOf(environmentId));
        labels.put(DEPLOYMENT_ID, String.valueOf(deploymentId));
        labels.put(DEPLOYMENT_NUMBER, String.valueOf(deploymentNumber));
        labels.put(COMMIT_SHA, commitSha == null ? "" : commitSha);
        labels.put(CREATED_AT, java.time.Instant.now().toString());
        return labels;
    }

    public static Map<String, String> managedFilter() {
        return Map.of(MANAGED, "true");
    }

    /**
     * Traefik auto-discovery labels. When the routing mode is {@code traefik}, these are added to
     * every application container so Traefik picks them up and routes
     * {@code https://<slug>.<baseDomain>} to the container's exposed port.
     */
    public static Map<String, String> traefikLabels(
            String projectSlug, int containerPort, String baseDomain) {
        String routerName = "df-" + projectSlug;
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("traefik.enable", "true");
        labels.put(
                "traefik.http.routers." + routerName + ".rule",
                "Host(`" + projectSlug + "." + baseDomain + "`)");
        labels.put("traefik.http.routers." + routerName + ".entrypoints", "websecure");
        labels.put("traefik.http.routers." + routerName + ".tls.certresolver", "letsencrypt");
        labels.put(
                "traefik.http.services." + routerName + ".loadbalancer.server.port",
                String.valueOf(containerPort));
        return labels;
    }
}
