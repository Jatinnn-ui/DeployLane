# API

Base path `/api/v1`. Interactive documentation is served by the backend at `/api/docs/ui`
(OpenAPI JSON at `/api/docs`).

## Authentication

Send `Authorization: Bearer <access token>` on every call except the public endpoints below. Obtain a token
by completing the GitHub OAuth flow, then calling `POST /api/v1/auth/refresh` (the refresh cookie is
HttpOnly, so the browser sends it automatically).

Public: `/api/v1/health`, `/api/v1/auth/config`, `/api/v1/auth/github/**`, `/api/v1/auth/refresh`,
`/api/v1/auth/logout`, `/api/v1/webhooks/**`, `/actuator/health`, `/api/docs/**`.

## Error format

Every non-2xx response uses one envelope:

```json
{
  "timestamp": "2026-02-11T12:43:21.884Z",
  "status": 409,
  "code": "INVALID_DEPLOYMENT_STATE",
  "message": "Only deployments that reached READY can be rolled back to. Deployment #47 is FAILED.",
  "requestId": "b31f0c7e",
  "path": "/api/v1/deployments/.../rollback",
  "fieldErrors": { "port": "must be between 1 and 65535" }
}
```

Switch on `code`, never on `message`. `requestId` also appears in the `X-Request-Id` response header and in
every server log line for that request.

Codes include `VALIDATION_FAILED`, `UNAUTHENTICATED`, `SESSION_EXPIRED`, `ACCESS_DENIED`, `NOT_FOUND`,
`CONFLICT`, `RATE_LIMITED`, `GITHUB_NOT_CONNECTED`, `GITHUB_API_ERROR`, `WEBHOOK_SIGNATURE_INVALID`,
`INVALID_DEPLOYMENT_STATE`, `ROLLBACK_NOT_POSSIBLE`, `RUNTIME_UNAVAILABLE`, `AI_UNAVAILABLE`,
`ENCRYPTION_ERROR`, `INTERNAL_ERROR`.

`SESSION_EXPIRED` specifically means "refresh and retry"; other 401s mean "sign in again".

## Endpoints

### Health
| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/health` | PostgreSQL, Redis, Docker and build storage. 200 when all up, 503 when degraded |

### Auth
| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/auth/config` | Whether GitHub OAuth is configured |
| GET | `/api/v1/auth/github/authorize` | 302 to GitHub; issues single-use state |
| GET | `/api/v1/auth/github/callback` | Verifies state, exchanges the code, sets the refresh cookie |
| POST | `/api/v1/auth/refresh` | Rotates the refresh token, returns an access token and the user |
| POST | `/api/v1/auth/logout` | Revokes the refresh token and clears the cookie |
| GET | `/api/v1/auth/me` | Current user and GitHub connection state |
| POST | `/api/v1/auth/ws-ticket` | Single use, 60 second ticket for the WebSocket handshake |

### Workspaces
`GET|POST /api/v1/workspaces`, `GET|PATCH /api/v1/workspaces/{id}`,
`GET /api/v1/workspaces/by-slug/{slug}`,
`GET|POST /api/v1/workspaces/{id}/members`,
`PATCH|DELETE /api/v1/workspaces/{id}/members/{memberId}`.

### GitHub
| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/github/connection` | Connection state and granted scopes |
| GET | `/api/v1/github/repositories?query=&page=&perPage=` | Searchable; served from a short lived per-user snapshot |
| GET | `/api/v1/github/repositories/{owner}/{repo}` | Repository metadata |
| GET | `/api/v1/github/repositories/{owner}/{repo}/branches` | Branches |
| GET | `/api/v1/github/repositories/{owner}/{repo}/commits/latest?ref=` | Latest commit on a ref |

### Detection
`POST /api/v1/detection` with `{ owner, repo, ref, rootDirectory? }` returns the framework, runtime,
install/build/start commands, port, package manager, **confidence** and the **evidence** behind it.

### Projects
| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/projects?workspaceId=&page=&size=` | Each item carries a latest-deployment snapshot |
| POST | `/api/v1/projects` | Import: verifies access, detects the framework, creates the production environment, optionally seeds variables |
| GET | `/api/v1/projects/{id}` | Includes environments, build config and the caller's permissions |
| PATCH | `/api/v1/projects/{id}` | Requires ADMIN or OWNER |
| DELETE | `/api/v1/projects/{id}` | Releases containers, images and build directories, then deletes data |

### Environments and variables
| Method | Path | Notes |
| --- | --- | --- |
| GET/POST | `/api/v1/projects/{projectId}/environments` | |
| PATCH/DELETE | `/api/v1/environments/{id}` | Change tracked branch, toggle auto deploy |
| GET | `/api/v1/environments/{id}/variables` | **Names and timestamps only - values are never returned** |
| POST | `/api/v1/environments/{id}/variables` | Value encrypted before storage |
| POST | `/api/v1/environments/{id}/variables/bulk` | `.env` paste; reports skipped lines |
| PATCH | `/api/v1/environment-variables/{id}` | Rotate a value |
| DELETE | `/api/v1/environment-variables/{id}` | |

### Deployments
| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/v1/projects/{projectId}/deployments` | **202** with a `QUEUED` deployment. Returns immediately |
| GET | `/api/v1/projects/{projectId}/deployments?environmentId=&page=&size=` | History, newest first |
| GET | `/api/v1/deployments/{id}` | Full detail including the step timeline |
| GET | `/api/v1/deployments/{id}/steps` | Timeline only |
| POST | `/api/v1/deployments/{id}/cancel` | Allowed while in flight |
| POST | `/api/v1/deployments/{id}/redeploy` | New deployment, same commit |
| POST | `/api/v1/deployments/{id}/rollback` | Roll back **to** this deployment, reusing its image |
| GET | `/api/v1/deployments/{id}/rollback-candidates` | Earlier successes whose image still exists |
| GET | `/api/v1/deployments/queue-status` | Queue depth and worker concurrency |

### Logs
`GET /api/v1/deployments/{id}/logs?afterSequence=&limit=&level=&source=` — cursor paginated:

```json
{ "items": [ { "sequence": 118, "timestamp": "...", "level": "ERROR", "source": "BUILD", "message": "npm ERR! ..." } ],
  "nextCursor": 118, "hasMore": false, "limit": 200 }
```

Pass the last `sequence` you have seen as `afterSequence` to fetch only new lines. Values are already
redacted server side. `GET .../logs/download` returns the same content as `text/plain`.

### Monitoring
`GET /api/v1/projects/{projectId}/health` — container state, uptime, restarts, latest sample.
`GET /api/v1/deployments/{id}/metrics?windowMinutes=` — current stats plus stored history.

### AI
| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/v1/ai/status` | Configured vs active analyzer |
| GET | `/api/v1/ai/tools` | The read-only tools the assistant may use |
| GET | `/api/v1/deployments/{id}/analysis` | Stored analysis; `VIEW_ANALYSIS` |
| POST | `/api/v1/deployments/{id}/analysis?force=` | Analyse a failed deployment; `USE_AI`, rate limited, cached |
| POST | `/api/v1/projects/{projectId}/ai/chat` | Project scoped question; returns the answer and which tools produced its context |

### Notifications and activity
`GET /api/v1/notifications`, `GET /api/v1/notifications/unread-count`,
`POST /api/v1/notifications/{id}/read`, `POST /api/v1/notifications/read-all`,
`GET /api/v1/activity`, `GET /api/v1/workspaces/{id}/activity`, `GET /api/v1/projects/{id}/activity`.

## WebSocket

Connect to `/ws/deployments/{deploymentId}?ticket=<ticket>` after obtaining a ticket from
`POST /api/v1/auth/ws-ticket`. Authorization is enforced at the handshake: the ticket is consumed and the
caller must hold `VIEW_LOGS` on the deployment's project.

Server to client events, each with an explicit `type`:

| Type | Payload |
| --- | --- |
| `CONNECTED` | `deploymentId`, `timestamp` |
| `LOG` | `sequence`, `level`, `source`, `message`, `timestamp` |
| `STATUS_CHANGED` | `status`, `statusLabel`, `deploymentNumber` |
| `STEP_UPDATED` | `step`, `stepLabel`, `status`, `durationMs`, `detail` |
| `DEPLOYMENT_READY` | `url`, `durationMs` |
| `DEPLOYMENT_FAILED` | `stage`, `message`, `exitCode` |
| `ANALYSIS_READY` | `severity`, `confidence` |
| `METRICS` | `cpuPercent`, `memoryBytes`, `memoryLimitBytes`, `restartCount`, `containerStatus` |

Send `ping` to receive `{"type":"PONG"}`; an idle socket behind a proxy dies silently without it. Slow
consumers are disconnected rather than allowed to buffer unbounded build output in server memory.

## Pagination

Offset pagination (`page`, `size`) for projects, deployments, notifications and activity, returning
`items`, `page`, `size`, `totalItems`, `totalPages`, `hasNext`, `hasPrevious`.

Cursor pagination for logs, because they are appended while being read.

## Rate limits

Per-minute limits on deployment creation, AI analysis, AI chat and webhooks; exceeding one returns **429**
with code `RATE_LIMITED`. Limits are configurable under `deployforge.rate-limit.*`.
