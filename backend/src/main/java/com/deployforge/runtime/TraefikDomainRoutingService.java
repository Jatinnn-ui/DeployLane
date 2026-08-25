package com.deployforge.runtime;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.project.ProjectDomain;
import com.deployforge.project.ProjectDomainRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traefik-based routing: containers get subdomain URLs instead of published host ports.
 *
 * <p>Traefik discovers containers via Docker labels (set by {@code ContainerStartStep}). This service
 * does not call Traefik at all — it computes the hostname, persists it to the {@code project_domains}
 * table, and returns the HTTPS URL. Traefik sees the container appear on the Docker network with the
 * right labels and starts routing traffic to it automatically.
 *
 * <p>Activated when {@code deployforge.deployment.routing-mode=traefik}.
 */
@Service
@Primary
@ConditionalOnProperty(name = "deployforge.deployment.routing-mode", havingValue = "traefik")
public class TraefikDomainRoutingService implements DomainRoutingService {

    private static final Logger log = LoggerFactory.getLogger(TraefikDomainRoutingService.class);

    private final ProjectDomainRepository domainRepository;
    private final DeployForgeProperties properties;

    public TraefikDomainRoutingService(
            ProjectDomainRepository domainRepository, DeployForgeProperties properties) {
        this.domainRepository = domainRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public RoutingTarget publish(
            UUID projectId, UUID environmentId, UUID deploymentId, String projectSlug, int hostPort) {

        String baseDomain = properties.deployment().baseDomain();
        String hostname = projectSlug + "." + baseDomain;
        String url = "https://" + hostname;

        // One SYSTEM domain row per environment, repointed on each promotion.
        domainRepository
                .findFirstByEnvironmentIdAndKind(environmentId, ProjectDomain.DomainKind.SYSTEM)
                .ifPresentOrElse(
                        existing -> {
                            if (!existing.getHostname().equals(hostname)) {
                                domainRepository.delete(existing);
                                domainRepository.flush();
                                domainRepository.save(
                                        new ProjectDomain(
                                                projectId,
                                                environmentId,
                                                hostname,
                                                hostPort,
                                                ProjectDomain.DomainKind.SYSTEM,
                                                ProjectDomain.DomainStatus.ACTIVE));
                            } else {
                                existing.setStatus(ProjectDomain.DomainStatus.ACTIVE);
                            }
                        },
                        () ->
                                domainRepository.save(
                                        new ProjectDomain(
                                                projectId,
                                                environmentId,
                                                hostname,
                                                hostPort,
                                                ProjectDomain.DomainKind.SYSTEM,
                                                ProjectDomain.DomainStatus.ACTIVE)));

        log.info(
                "traefik_routing_published environment={} deployment={} url={}",
                environmentId,
                deploymentId,
                url);
        return new RoutingTarget(url, hostname, hostPort);
    }

    @Override
    @Transactional
    public void unpublish(UUID environmentId, UUID deploymentId) {
        domainRepository
                .findFirstByEnvironmentIdAndKind(environmentId, ProjectDomain.DomainKind.SYSTEM)
                .ifPresent(domain -> domain.setStatus(ProjectDomain.DomainStatus.DISABLED));
        log.info("traefik_routing_unpublished environment={} deployment={}", environmentId, deploymentId);
    }

    @Override
    public String previewUrl(String projectSlug, int hostPort) {
        return "https://" + projectSlug + "." + properties.deployment().baseDomain();
    }
}
