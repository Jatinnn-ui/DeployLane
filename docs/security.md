# Security model

DeployForge runs code from GitHub repositories on a host with a Docker engine. That is inherently
privileged, so this document is explicit about what is defended, what is accepted, and what would have to
change for untrusted input.

## Threat model in one line

**The platform defends its own secrets and its tenants from each other. It does not sandbox the code you
choose to deploy.** Deploy repositories you trust.

## Authentication

GitHub OAuth is the only way in. There are no local passwords, so there is no password store to breach.

```mermaid
sequenceDiagram
    participant B as Browser
    participant A as Backend
    participant G as GitHub

    B->>A: GET /api/v1/auth/github/authorize
    A->>A: issue state (Redis, single use) + HttpOnly state cookie
    A-->>B: 302 to GitHub
    B->>G: consent
    G-->>A: callback?code&state
    A->>A: state must exist in Redis AND match the cookie
    A->>G: exchange code for token (server side)
    A->>G: fetch profile
    A->>A: upsert user, encrypt token, provision workspace
    A-->>B: HttpOnly refresh cookie, 302 to the SPA
    B->>A: POST /api/v1/auth/refresh
    A-->>B: short lived access token (kept in memory)
```

- **OAuth state** is verified twice: it must still exist in Redis (single use, 10 minute TTL) *and* match
  the browser's `df_oauth_state` cookie. An attacker can supply one, never both.
- **Access tokens** are 15 minute HS256 JWTs held in browser memory only. Not `localStorage`, so an XSS bug
  cannot steal a durable credential.
- **Refresh tokens** are opaque, `HttpOnly`, `SameSite=Lax`, scoped to `/api/v1/auth`, stored **hashed**
  (SHA-256) in Redis, and **rotated on every use**. Token theft becomes short lived and detectable.
- **GitHub tokens** never reach the browser. The frontend calls DeployForge; DeployForge calls GitHub.
- The `returnTo` parameter is validated as a same-site relative path, so the login endpoint cannot be used
  as an open redirect.

## Authorization

Workspace roles, checked server side on every request by `AuthorizationService`:

| Permission              | OWNER | ADMIN | DEVELOPER | VIEWER |
| ----------------------- | :---: | :---: | :-------: | :----: |
| View project/logs/monitoring/analysis | ✓ | ✓ | ✓ | ✓ |
| Deploy, redeploy, cancel | ✓ | ✓ | ✓ | |
| Use AI (analysis, chat)  | ✓ | ✓ | ✓ | |
| Rollback                 | ✓ | ✓ | | |
| Manage project / environments / variables | ✓ | ✓ | | |
| Delete project           | ✓ | ✓ | | |
| Manage members, workspace | ✓ | | | |

- Roles are **never** carried in the JWT. Membership is read from the database on every check, so revoking
  access takes effect immediately.
- Non-members receive **404**, not 403: the existence of another tenant's project is not observable.
  Members who merely lack a permission get 403, which is actionable for them.
- Every project scoped path goes through `ProjectAccessGuard`, which loads and authorizes in one call, so a
  new endpoint cannot silently skip the check.
- Role changes cannot escalate: you may not grant a role at or above your own.
- The matrix is asserted in `WorkspaceRoleTest` - it is policy, so it is tested.

## Secrets

**At rest** — AES-256-GCM (authenticated encryption) applied in the application before the value reaches
PostgreSQL. Format `v1:base64(iv || ciphertext || tag)`; a fresh 12-byte IV per encryption. Applies to
GitHub OAuth tokens and every environment variable value. A database dump alone is useless.

The key comes from `ENCRYPTION_KEY` (base64, exactly 32 bytes) and the application **refuses to start**
without it. Losing it makes stored secrets unrecoverable; that is the intended property.

**In the API** — there is no endpoint that returns a stored value. Not for viewers, not for owners. Reads
return names and timestamps; a value can only be replaced. The UI's masking is presentation, not the
control.

**In logs** — `SecretRedactionService` runs on every log line *before it is persisted*, using two
strategies:

1. literal values registered for the deployment (the decrypted variables actually injected)
2. heuristics for shapes DeployForge was never told about: `scheme://user:password@host`, GitHub / Google /
   OpenAI / Anthropic / Slack / AWS token prefixes, JWTs, PEM private key blocks, and
   `SOMETHING_SECRET=value` assignments

**Toward AI providers** — variable **names** only, plus build configuration and already-redacted log lines.
The context object handed to a provider has no field that could carry a value, and the model's response is
redacted again on the way out in case it echoed something back.

**Never logged** — tokens, passwords, variable values, AI keys. Error responses carry a code and a request
id; the underlying SQL or stack trace stays on the server.

## Running user code

Every application container gets:

- **No privileges** — never `--privileged`, always `no-new-privileges:true`
- **No host access** — no bind mounts, and never the Docker socket
- **Dropped capabilities** — `SYS_ADMIN`, `SYS_MODULE`, `SYS_PTRACE`, `SYS_RAWIO`, `NET_RAW`, `MKNOD`,
  `AUDIT_WRITE`
- **Hard limits** — memory (with swap pinned to the same value, so the limit cannot be escaped), CPU via
  `NanoCPUs`, and a PID limit
- **Network isolation** — attached to a dedicated bridge network (`deployforge-apps`), not host networking
  and not the network carrying PostgreSQL and Redis
- **A non-root user** wherever the base image provides one
- **A build timeout**, cancellable mid-flight

### No shell string ever
There is no `Runtime.exec("docker build " + input)` anywhere. Docker is driven through the Engine API with
typed arguments, and cloning uses JGit in-process - which also keeps the OAuth token out of any process
argument list. Build and start commands are validated against shell control characters
(`; | & \` $ < > \`), so a command cannot escape into a second statement. Users who need arbitrary shell
logic commit their own Dockerfile, which is an explicit, reviewable choice.

### Filesystem containment
Working directories are `<root>/<deploymentId>/source`, where the id is a platform-generated UUID. Every
subsequent join goes through `SafePaths.resolveInside`, which normalises and then verifies containment, so a
crafted root directory cannot read outside the checkout. Covered by `ValidatorsTest`.

### What is accepted
Deployed code runs as a normal container process. It can consume its CPU and memory allowance, make
outbound network calls, and read the variables you gave it. It cannot reach the platform's database, the
Docker socket or the host filesystem. For genuinely untrusted code you would add gVisor or Firecracker,
per-tenant hosts and egress filtering - out of scope here, and stated rather than implied.

### The compose caveat
Running the backend from `docker-compose.yml` mounts the host Docker socket into the backend container.
Socket access is equivalent to host root. It is the standard trade-off for a self-hosted deployment
platform, it is documented at the top of the compose file, and application containers never receive it.

## Webhooks

The endpoint is public because GitHub calls it, so it is defended by three things instead of a session:

1. **HMAC-SHA256** verification of `X-Hub-Signature-256` over the exact raw bytes, compared with
   `MessageDigest.isEqual` to avoid a timing side channel. No secret configured means every webhook is
   rejected, not accepted.
2. **Idempotency** on the GitHub delivery id, so a retry cannot deploy twice.
3. **Rate limiting**.

Verification is covered by `GitHubWebhookVerifierTest`, including a valid signature over a tampered body.

## CSRF, CORS, transport

Every state changing API call is authenticated with an `Authorization` header, which a cross-site form
cannot set, so Spring's CSRF filter is disabled deliberately rather than by accident. Exactly two endpoints
trust a cookie - `/auth/refresh` and `/auth/logout` - and they are protected by `SameSite=Lax` plus a strict
CORS allow-list, so a third party site can neither read the response nor obtain a token. CORS allows one
origin: `FRONTEND_URL`. Set `COOKIE_SECURE=true` behind HTTPS; the startup validator warns when the backend
URL is HTTPS but the flag is not set.

## AI safety

The assistant can read and explain. It cannot act:

- no tool deploys, restarts, rolls back, deletes, rotates a secret or touches a repository
- no tool executes a shell command; the model never receives an execution channel
- the model cannot query anything - the backend runs a fixed set of read-only tools and passes the result
- suggested commands are filtered: `rm -rf`, `docker system prune`, `docker rm/rmi/kill`, `DROP TABLE`,
  `TRUNCATE`, `kubectl delete`, `git push --force`, `curl | sh`, `chmod 777`, `mkfs`, `dd if=` and reboot
  are stripped even if the model returns them
- model output is validated before storage, not trusted: missing root cause is rejected, confidence is
  clamped, unknown severities are normalised, list lengths capped

Covered by `AiResponseParserTest`.

## Availability

Rate limits (Redis counters) on deployment creation, AI analysis, AI chat and webhooks. They **fail open**:
if Redis is unreachable, requests are served without limiting, because losing rate limiting is less harmful
than taking the API down - and Redis health is reported separately by `/api/v1/health`.

A single deployment cannot take the platform down. Pipeline exceptions are contained per deployment, worker
threads survive any single failure, publishing and notification failures are swallowed by contract, and
`DeploymentWorker` fails interrupted deployments on restart instead of leaving them hanging.

## Checklist

| Control                                  | Status |
| ---------------------------------------- | ------ |
| OAuth state validation (Redis + cookie)  | ✓ |
| CSRF strategy (bearer tokens, documented)| ✓ |
| CORS allow-list                          | ✓ |
| HttpOnly, SameSite, configurable Secure cookies | ✓ |
| Refresh token rotation + hashed at rest  | ✓ |
| Webhook HMAC verification                | ✓ |
| JWT verification (signature, expiry, issuer, type) | ✓ |
| Rate limiting                            | ✓ |
| Server side RBAC                         | ✓ |
| Secret encryption (AES-256-GCM)          | ✓ |
| Secret redaction before storage          | ✓ |
| Input validation (keys, branches, paths, commands, ports) | ✓ |
| Container isolation, dropped capabilities, no privileges | ✓ |
| Filesystem path containment              | ✓ |
| CPU / memory / PID limits                | ✓ |
| Build timeout and cancellation           | ✓ |
| AI output validation and command filtering | ✓ |
| Untrusted-code sandboxing (gVisor / microVM) | Documented limitation |
| Automatic TLS certificates               | Not implemented (reverse proxy concern) |
| Dependency vulnerability scanning        | Not implemented (CI concern) |
