package com.deployforge.runtime;

import com.deployforge.config.DeployForgeProperties;
import com.deployforge.project.ProjectDomain;
import com.deployforge.project.ProjectDomainRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single node routing: publish the container on a host port and address it directly.
 *
 * <p>This is the honest local strategy - no wildcard DNS, no TLS automation, no fake vanity domains.
 * The {@code project_domains} row it maintains is the same shape a proxy based implementation would
 * write, so switching to Traefik later is a new implementation of
 * {@link DomainRoutingService} rather than a data migration.
 *
 * <p>Active by default, or when {@code deployforge.deployment.routing-mode=local}.
 */
@Service
@ConditionalOnProperty(name = "deployforge.deployment.routing-mode", havingValue = "local", matchIfMissing = true)
public class LocalPortDomainRoutingService implements DomainRoutingService {

    private static final Logger log = LoggerFactory.getLogger(LocalPortDomainRoutingService.class);

    private final ProjectDomainRepository domainRepository;
    private final DeployForgeProperties properties;

    public LocalPortDomainRoutingService(
            ProjectDomainRepository domainRepository, DeployForgeProperties properties) {
        this.domainRepository = domainRepository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public RoutingTarget publish(
            UUID projectId, UUID environmentId, UUID deploymentId, String projectSlug, int hostPort) {
        String host = properties.deployment().publicHost();
        String hostname = host + ":" + hostPort;
        String url = "http://" + hostname;

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
                                existing.setTargetPort(hostPort);
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
                "routing_published environment={} deployment={} url={}", environmentId, deploymentId, url);
        return new RoutingTarget(url, hostname, hostPort);
    }

    @Override
    @Transactional
    public void unpublish(UUID environmentId, UUID deploymentId) {
        domainRepository
                .findFirstByEnvironmentIdAndKind(environmentId, ProjectDomain.DomainKind.SYSTEM)
                .ifPresent(domain -> domain.setStatus(ProjectDomain.DomainStatus.DISABLED));
        log.info("routing_unpublished environment={} deployment={}", environmentId, deploymentId);
    }

    @Override
    public String previewUrl(String projectSlug, int hostPort) {
        return "http://" + properties.deployment().publicHost() + ":" + hostPort;
    }
}
