# Hostname routing with Traefik

This directory documents how DeployForge would move from **published host ports** to **hostname routing**
(`https://<project-slug>.deployforge.example.com`). It is a design note and a working starting point, not a
configuration this repository exercises in tests - so it is presented as exactly that.

## Why it is not the default

The local implementation publishes a container port and returns `http://localhost:<port>`. That is honest
for a single node install: no wildcard DNS, no certificate authority, no proxy to keep alive. Hostname
routing needs a wildcard DNS record and TLS automation, neither of which belongs in a `docker compose up`
quickstart.

## Where it plugs in

Everything routing-related lives behind one interface:

```java
public interface DomainRoutingService {
    RoutingTarget publish(UUID projectId, UUID environmentId, UUID deploymentId,
                          String projectSlug, int hostPort);
    void unpublish(UUID environmentId, UUID deploymentId);
    String previewUrl(String projectSlug, int hostPort);
}
```

`LocalPortDomainRoutingService` is the current implementation. A `TraefikDomainRoutingService` would be a
second one. It does not need to talk to Traefik at all: Traefik discovers containers through Docker labels,
so publishing a route means **starting the container with the right labels**, and the `project_domains` row
the platform already writes carries the hostname.

The labels a container would need:

```
traefik.enable=true
traefik.http.routers.df-<slug>.rule=Host(`<slug>.deployforge.example.com`)
traefik.http.routers.df-<slug>.entrypoints=websecure
traefik.http.routers.df-<slug>.tls.certresolver=letsencrypt
traefik.http.services.df-<slug>.loadbalancer.server.port=<containerPort>
```

Two behaviours would improve as a result:

- **Promotion becomes a label switch** rather than a port change, so the public URL is stable across
  deployments instead of changing with the allocated port.
- **Host ports stop being published at all**, which removes the port range as a scaling limit and reduces
  the host's exposed surface.

## Starting point

```yaml
services:
  traefik:
    image: traefik:v3.1
    command:
      - --providers.docker=true
      - --providers.docker.exposedbydefault=false
      # Only containers on the application network, so the control plane is never auto-exposed.
      - --providers.docker.network=deployforge-apps
      - --entrypoints.web.address=:80
      - --entrypoints.websecure.address=:443
      - --entrypoints.web.http.redirections.entrypoint.to=websecure
      - --certificatesresolvers.letsencrypt.acme.email=ops@example.com
      - --certificatesresolvers.letsencrypt.acme.storage=/acme/acme.json
      - --certificatesresolvers.letsencrypt.acme.dnschallenge=true
    ports: ['80:80', '443:443']
    volumes:
      # Read only: Traefik needs to discover containers, not control the engine.
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - traefik-acme:/acme
    networks: [deployforge-apps]

volumes:
  traefik-acme:

networks:
  deployforge-apps:
    external: true
```

A wildcard certificate for `*.deployforge.example.com` requires the DNS challenge, hence
`dnschallenge=true` plus the provider credentials for your DNS host.

## What to be careful about

- **Wildcard DNS first.** `*.deployforge.example.com` must resolve to the host before any of this works.
- **The socket is read only** here. Traefik discovers; it does not build or run anything.
- **Do not expose the control plane by accident.** `exposedbydefault=false` and scoping the provider to the
  application network are both load-bearing.
- **Slug collisions across workspaces.** Project slugs are unique per workspace, not globally, so a hostname
  scheme needs the workspace slug too - `<project>.<workspace>.example.com`.
- **Preview deployments** would produce `pr-42-<project>.example.com` and must be torn down when the pull
  request closes, or the certificate and route count grows without limit.
