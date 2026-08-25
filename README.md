<div align="center">

# DeployLane

**Deploy. Monitor. Debug. Fix.**

A self-hosted deployment platform that takes a GitHub repository, builds it into a Docker image, runs it
with resource limits, streams the build live, monitors the container, and explains the failure when
something breaks.

[Architecture](docs/architecture.md) · [Pipeline](docs/deployment-pipeline.md) ·
[Security](docs/security.md) · [API](docs/api.md) · [Schema](docs/database-schema.md) ·
[AI analysis](docs/ai-analysis.md) · [Local setup](docs/local-development.md)

</div>

---

## What it actually does

This is not a UI over a mocked backend. A deployment really clones the repository, really builds an image,
really starts a container, really health checks it and really publishes a URL. When it fails, the failure is
real, the logs are its own, and the analysis is derived from them.

```
Login with GitHub → Import repository → Select branch → Detect framework
      → Configure build → Add environment variables → Deploy
      → Clone → Build image (install + build) → Start container
      → Stream logs → Health check → Live URL

On failure: capture logs → analyse → root cause + evidence + fix → redeploy
```

## Screenshots

> Placeholder. Capture from a local run: the dashboard, the deployment detail screen mid-build with the log
> terminal streaming, and the AI analysis card on a failed deployment.

| | |
| --- | --- |
| `docs/images/dashboard.png` | Project grid with live status per project |
| `docs/images/deployment-live.png` | Deployment detail: timeline, streaming logs |
| `docs/images/analysis.png` | AI analysis: root cause, evidence, suggested fix |
| `docs/images/monitoring.png` | CPU and memory over time, uptime, restarts |

## Features

**Deployment**
- GitHub OAuth sign-in, repository import, branch selection
- Framework detection with **confidence and the evidence behind it** - Next.js, React/Vite, Vue, Astro,
  SvelteKit, CRA, Express, NestJS, plain Node, Spring Boot (Maven and Gradle), FastAPI, Flask, Django,
  static sites, and any repository with its own Dockerfile
- Dockerfile generation per framework when the repository has none, stored as reviewable build metadata
- Asynchronous, queued pipeline with a strict state machine and per-environment exclusivity
- Cancellation mid-build, redeploy, and rollback that reuses a previously built image
- Zero-downtime promotion: the old container serves until the new one is healthy, so a failed deploy is
  **not** an outage
- Auto deploy from GitHub push webhooks, with HMAC verification and delivery-id idempotency

**Observability**
- Live logs over WebSocket with a terminal viewer: auto-scroll that pauses when you scroll up, search,
  level and source filters, timestamps, copy and download
- Per-step timeline with real measured durations
- Container CPU, memory, uptime and restart tracking, sampled every 30s and charted
- In-app notifications and an append-only activity trail

**AI**
- Failure analysis producing root cause, quoted evidence, suggested fixes, severity and confidence
- Provider abstraction: Gemini, OpenAI, or a **local rule based analyzer** that needs no API key
- Project scoped DevOps chat, restricted to read-only backend tools - the model cannot run anything
- Secret values are never sent to a provider; variable **names** only

**Security**
- AES-256-GCM encryption for GitHub tokens and environment variables, applied before storage
- No API endpoint returns a stored secret value, ever
- Server side secret redaction on every log line before it is persisted
- Workspace RBAC (OWNER / ADMIN / DEVELOPER / VIEWER) resolved from the database on every request
- Rotating refresh tokens hashed at rest, access tokens in memory only
- Containers run unprivileged with dropped capabilities and hard CPU / memory / PID limits

## Technology

| Layer | Choices |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query, Zustand, React Router, Recharts, Radix primitives, Framer Motion, native WebSocket |
| Backend | Java 21, Spring Boot 3.5 (Web, Security, Data JPA, Validation, WebSocket, Actuator), Flyway, JGit, docker-java, jjwt, springdoc |
| Data | PostgreSQL 16, Redis 7 |
| Runtime | Docker Engine API, isolated bridge network, published host ports (Traefik documented as the hostname-routing alternative) |
| Tests | JUnit 5, AssertJ, Testcontainers (tagged), Vitest, React Testing Library |

## Quick start

```bash
git clone <this-repo> && cd deployforge-ai

# Generates JWT_SECRET and ENCRYPTION_KEY into .env
./scripts/generate-secrets.sh            # Windows: ./scripts/generate-secrets.ps1

docker compose up -d postgres redis

set -a && . ./.env && set +a             # Spring Boot reads env vars, not .env
cd backend  && ./mvnw spring-boot:run    # http://localhost:8080
cd frontend && npm install && npm run dev # http://localhost:5173
```


./scripts/run-backend-dev.ps1
backend 


On Windows the backend step is `./scripts/run-backend-dev.ps1`, which loads `.env` into the process
first because PowerShell cannot `source` it.

Docker must be running - the platform builds images through it. Add a GitHub OAuth app
(callback `http://localhost:8080/api/v1/auth/github/callback`) to enable sign-in. Full instructions,
including webhook tunnelling and troubleshooting, are in [docs/local-development.md](docs/local-development.md).

## Configuration

| Variable | Required | Purpose |
| --- | :---: | --- |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | ✓ | PostgreSQL |
| `REDIS_HOST`, `REDIS_PORT` | ✓ | Queue, locks, rate limits, sessions |
| `JWT_SECRET` | ✓ | Access token signing, ≥32 bytes |
| `ENCRYPTION_KEY` | ✓ | AES-256 key, base64, exactly 32 bytes |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | | OAuth login and repository import |
| `GITHUB_WEBHOOK_SECRET` | | Auto deploy on push |
| `AI_PROVIDER` | | `heuristic` (default), `gemini` or `openai` |
| `GEMINI_API_KEY` / `OPENAI_API_KEY` | | Model backed analysis |
| `FRONTEND_URL`, `PUBLIC_BACKEND_URL` | | CORS origin and OAuth callback |
| `DEPLOYMENT_ROOT_PATH` | | Where clones and build contexts live |
| `DOCKER_HOST_URI` | | `npipe:////./pipe/docker_engine` on Windows, `unix:///var/run/docker.sock` elsewhere |
| `DEPLOYMENT_MEMORY_LIMIT_MB`, `DEPLOYMENT_CPU_LIMIT`, `DEPLOYMENT_PIDS_LIMIT` | | Per-container limits |

The application **refuses to start** without `JWT_SECRET` and `ENCRYPTION_KEY`, and warns loudly about every
optional integration that is not configured. See [.env.example](.env.example).

## How deployments work

Detail in [docs/deployment-pipeline.md](docs/deployment-pipeline.md). In short:

`POST /deployments` writes a `QUEUED` row and returns in milliseconds. A worker picks it up, takes a
per-environment lock, and runs eight steps: clone (shallow, JGit), detect, write the build plan, build the
image (this is where `npm ci` and `npm run build` actually run), start the container, health check, promote.
Each step advances the status through `DeploymentStateMachine`, appends logs, and pushes events over
WebSocket.

Traffic switches only after the health check passes, and the previous container is stopped only after that
switch.

## Security model

Full detail in [docs/security.md](docs/security.md). The one-line summary:

> **The platform defends its own secrets and its tenants from each other. It does not sandbox the code you
> choose to deploy.** Deploy repositories you trust.

Containers are unprivileged, receive no host mounts and never the Docker socket, run with dropped
capabilities on an isolated network with hard resource limits. Docker is driven through the Engine API with
typed arguments - there is no shell string anywhere for user input to escape.

## Try the failure path

`examples/` contains three apps: one that works, one that fails during the build (undeclared dependency), and
one that fails at startup (missing environment variable). Every failure is genuine, and each is explained
differently because it fails at a different stage. See [examples/README.md](examples/README.md).

## Project layout

```
backend/    Spring Boot: auth, workspace, project, environment, github, detection,
            deployment (queue + pipeline + state machine), runtime (Docker), log,
            monitoring, ai, webhook, notification, activity, cleanup, security
frontend/   React dashboard: api hooks, ui kit, feature screens, log terminal
examples/   One working app, two that fail on purpose
infra/      nginx reverse proxy for the control plane, Traefik design note
docs/       Architecture, pipeline, security, schema, API, AI analysis, setup
scripts/    Secret generation
```

## Tests

```bash
cd backend  && ./mvnw test        # 134 tests, no Docker or database required
cd frontend && npm run typecheck && npm test
```

The backend suite covers the state machine transition table, AES-GCM round trip and tamper detection, secret
redaction, webhook signature verification, framework detection across eight repository shapes, the
permission matrix, Dockerfile generation, path traversal and command injection rejection, and AI response
validation including destructive-command filtering. Tests needing a live Docker engine are tagged `docker`
and excluded by default so the suite is green anywhere.

## Known limitations

Stated rather than implied:

- **Single node.** One host, one Docker engine. The `DeploymentRuntime` interface is where a second node or
  Kubernetes would attach; neither is implemented.
- **No sandboxing of deployed code.** Containers are hardened, not isolated to a VM boundary. Trusted
  repositories only.
- **Published ports, not hostnames.** Deployments are reached at `http://localhost:<port>`. Hostname routing
  and TLS are documented in `infra/traefik/` but not wired up.
- **Application logs are bounded.** Container output is captured during startup and health checking, not
  streamed indefinitely - long-term application logging is a separate concern from deployment logs.
- **No request metrics.** Nothing sits in the request path on a single node, so no request-rate chart is
  shown rather than inventing one.
- **Preview deployments are partial.** A pull request deploys to a PREVIEW environment if one already tracks
  that branch; automatic create-and-destroy is not implemented.
- **Build cache is not shared.** Each build uses Docker's local layer cache only.

## Roadmap

Preview deployments with automatic teardown · custom domains and automatic HTTPS · team invitations ·
Slack and Discord notifications · deployment comments on pull requests · deployment approvals ·
resource quotas · multiple deployment nodes · build cache sharing · AI generated Dockerfiles ·
AI configuration suggestions · incident timeline.

---

## Engineering challenges

The parts of this project worth discussing, and what the trade-off actually was.

**Running long deployments without blocking anything.** A deployment takes minutes; an HTTP request must not.
The API writes a `QUEUED` row and hands an id to a Redis list; bounded workers consume it. The harder half
was transactions: the pipeline never holds one across a clone, a build or a probe, so every mutation goes
through `DeploymentStateService` in a short, explicit transaction. `DeploymentContext` carries plain values,
not entities, precisely so no persistence context can leak into a five-minute operation.

**A state machine, because deployments race.** Status updates scattered across steps eventually produce
`FAILED → READY`. One class owns transitions, they are forward-only, aborts are always legal, terminal states
are terminal, and the table is unit tested. The single exception - `QUEUED → STARTING` for rollbacks - is
documented at the line that allows it.

**Streaming logs that stay readable.** Three problems, three answers. Volume: lines are buffered and flushed
in batches instead of one insert per line, and only a bounded slice is rendered in the DOM. Continuity: the
client merges a REST history page with the live socket by sequence number, so the boundary is invisible and
duplicate-free. Usability: auto-scroll pauses the moment you scroll up, because a log that yanks you away
mid-read is worse than no log.

**Authenticating a WebSocket.** Browsers cannot set headers on a handshake, and putting a 15 minute token in
a URL leaks it into proxy logs. The SPA exchanges its bearer token for a single-use 60 second ticket over
HTTPS and passes only that; authorization is checked once, at the handshake.

**Secrets that survive a database dump.** AES-256-GCM before insert, key from the environment, application
refuses to start without it. The design decision that mattered most: **no read path returns a value.** Not
for owners. A secret can be replaced, never retrieved - which removed a whole class of "who can see this"
questions. Redaction then runs on every log line *before storage*, with literal values plus heuristics for
shapes we were never told about.

**Running other people's code.** Unprivileged, `no-new-privileges`, dropped capabilities, no host mounts,
never the Docker socket, hard memory/CPU/PID limits, isolated bridge network, time-boxed builds. And
structurally: no shell string anywhere - Docker via the Engine API with typed arguments, cloning via JGit
in-process so the token never appears in a process list, filesystem joins through a containment check. The
honest part is the boundary: this is hardening, not a VM sandbox, and the README says so.

**Zero-downtime with one host.** Start the new container, health check it, *then* switch routing, *then* stop
the old one. Cheap to implement, and it converts "the deploy failed" from an outage into a non-event. The
health check also inspects container state between probes, so a crash-looping container fails in seconds
instead of burning a 90 second timeout - and its output becomes the evidence for analysis.

**Rollback without rewriting history.** A rollback is a new deployment with `triggerType=ROLLBACK` that
reuses the target's image, which makes it fast and byte-identical to what previously ran. Deployments
snapshot their own build configuration, so a rollback cannot silently pick up settings changed since.

**AI that is checkable.** The interesting work was not the prompt, it was treating model output as untrusted
input: extract JSON from prose or fences, reject a response with no root cause, clamp confidence, normalise
severity, cap lengths, and **strip destructive commands even when the model suggests them**. Evidence is part
of the output so a developer can verify the conclusion. The fallback matters too - a local rule based
analyzer means failure analysis works with no API key, and it is labelled as rules everywhere so nobody
mistakes it for a model.

**Webhook idempotency across instances.** GitHub retries. An in-memory guard fails the moment there are two
instances, so the delivery id is inserted first and a unique-constraint violation *is* the duplicate check.

**Modules that stay layered.** Deployment depends on project, so the projects list showing deployment status
would have closed a cycle. Two small ports invert it: `ProjectDeploymentSnapshotProvider` and
`FailureAnalysisTrigger` are declared where they are consumed and implemented where the data lives - which is
also what lets the pipeline request AI analysis without depending on the AI module at all.

## License

MIT
