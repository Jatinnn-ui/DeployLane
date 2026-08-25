# Local development

## Requirements

| Tool | Version | Why |
| --- | --- | --- |
| Java | 21+ | Backend. Compiled with `--release 21`; a newer JDK works |
| Node.js | 20+ | Frontend |
| Docker | 24+ | **Required.** The platform builds images and runs containers through the engine |
| Git | any | Only for your own workflow - cloning uses JGit in-process |

Maven is not required: use the wrapper (`backend/mvnw`).

Docker must be running before the first deployment. Without it the API still starts and
`/api/v1/health` reports exactly which component is unavailable, which is the intended behaviour rather
than a crash at boot.

## Setup

```bash
# 1. Secrets. Generates JWT_SECRET and ENCRYPTION_KEY into .env
./scripts/generate-secrets.sh          # Windows: ./scripts/generate-secrets.ps1

# 2. Datastores
docker compose up -d postgres redis

# 3. Backend (Flyway migrates on startup)
set -a && . ./.env && set +a          # application.yml reads env vars, not .env
cd backend && ./mvnw spring-boot:run

# 4. Frontend
cd frontend && npm install && npm run dev
```

On Windows, PowerShell has no equivalent of `source`, so step 3 is a script instead:

```powershell
./scripts/run-backend-dev.ps1
```

It loads `.env` into the process environment, fails fast if `JWT_SECRET` or `ENCRYPTION_KEY` is
missing, and then starts Maven. Spring Boot does not read `.env` files itself, so starting the backend
without one of these two steps silently falls back to the defaults in `application.yml` and startup
validation rejects the empty secrets.

Dashboard on <http://localhost:5173>, API on <http://localhost:8080>, API docs at
<http://localhost:8080/api/docs/ui>.

### Port already in use

`POSTGRES_PORT` and `REDIS_PORT` in `.env` set the *host* side of the published port, so a machine
already running Postgres on 5432 or Redis on 6379 for another project needs only:

```dotenv
POSTGRES_PORT=5442
REDIS_PORT=6389
DATABASE_URL=jdbc:postgresql://localhost:5442/deployforge
```

The container side stays 5432/6379, so nothing inside compose changes.

Running the datastores in Docker and the two applications on the host is the recommended loop: hot reload
works, and the backend talks to your host Docker engine directly.

### Full stack in Docker

```bash
docker compose up -d --build
```

Read the security note at the top of `docker-compose.yml` first: the backend container mounts the host
Docker socket, which is equivalent to host root.

## GitHub OAuth

1. <https://github.com/settings/developers> → **New OAuth App**
2. Homepage URL `http://localhost:5173`
3. Authorization callback URL `http://localhost:8080/api/v1/auth/github/callback`
4. Put the client id and secret in `.env` and restart the backend

The requested scopes are `read:user`, `user:email` and `repo`. `repo` is what allows cloning private
repositories and managing webhooks; without it only public repositories can be deployed.

Until this is configured the login page says so explicitly instead of failing on click.

## Docker host

| Environment | `DOCKER_HOST_URI` |
| --- | --- |
| Windows (Docker Desktop) | `npipe:////./pipe/docker_engine` |
| macOS / Linux | `unix:///var/run/docker.sock` |
| Backend inside compose | `unix:///var/run/docker.sock` (socket mounted) |

Deployed containers publish a host port from `DEPLOYMENT_PORT_RANGE_START..END` (default 30000-30999), so a
deployment is reachable at `http://localhost:<port>`.

## AI provider

Default is `AI_PROVIDER=heuristic`: a local rule based analyzer, no API key, no network calls. Failure
analysis works out of the box and is labelled `heuristic` everywhere it appears.

For model backed analysis:

```dotenv
AI_PROVIDER=gemini
GEMINI_API_KEY=...
# or
AI_PROVIDER=openai
OPENAI_API_KEY=...
```

If the configured provider is unreachable or over quota, analysis falls back to the local analyzer and says
so in the log rather than showing an empty card.

## Webhooks from a laptop

GitHub cannot reach `localhost`, so expose the backend with a tunnel:

```bash
cloudflared tunnel --url http://localhost:8080
# or
ngrok http 8080
```

Then in the repository's **Settings → Webhooks**:

- Payload URL: `https://<tunnel-host>/api/v1/webhooks/github`
- Content type: `application/json`
- Secret: the same value as `GITHUB_WEBHOOK_SECRET`
- Events: **Pushes** and **Pull requests**

Set `PUBLIC_BACKEND_URL` to the tunnel host too, so the OAuth callback still matches. Unsigned or
wrongly signed webhooks are rejected, and GitHub's delivery id makes retries idempotent.

## Tests

```bash
cd backend  && ./mvnw test          # unit tests, no Docker needed
cd backend  && ./mvnw test -Dgroups=docker   # Testcontainers based tests
cd frontend && npm test             # Vitest + React Testing Library
cd frontend && npm run typecheck
```

The default backend suite deliberately needs neither Docker nor a database: it covers the state machine,
encryption, redaction, webhook signatures, framework detection, the permission matrix, Dockerfile
generation, input validation and AI response parsing. Tests requiring a live engine are tagged `docker` and
excluded from the default build so `mvn verify` stays green anywhere.

## Verifying a deployment end to end

1. Sign in with GitHub.
2. **Import repository** → choose a repository → branch → confirm detection.
3. Add any variables the app needs under **Environment**.
4. **Deploy**, and watch the log stream: clone → detect → build plan → image build → container start →
   health check → ready.
5. Open the deployment URL.
6. Then break it on purpose - `examples/broken-env-app` or `examples/broken-build-app` - to see the failure
   analysis, fix it, and redeploy.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| Backend exits with "ENCRYPTION_KEY is not configured" | Run `scripts/generate-secrets.sh`. Refusing to start is intentional |
| `/api/v1/health` shows docker DOWN | Docker not running, or the wrong `DOCKER_HOST_URI` for your OS |
| Login button says OAuth is not configured | `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` missing |
| Deployment stuck in `QUEUED` | Redis unreachable, or no worker: check `/api/v1/health` and the backend log |
| Health check times out | The app must listen on `0.0.0.0` and the injected `PORT`; `127.0.0.1` is unreachable from outside the container |
| "No free host port" | Widen `DEPLOYMENT_PORT_RANGE_*` or delete unused projects |
| Private repository fails to clone | The `repo` scope was declined; sign out and in again to grant it |
| Log stream shows "Stream unavailable" | WebSocket blocked, or Redis down so tickets cannot be issued. Logs still load over REST |
