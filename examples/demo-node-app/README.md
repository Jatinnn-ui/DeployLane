# demo-node-app

A deliberately tiny Node.js service used to demonstrate a **successful** DeployForge deployment.

No dependencies, so `npm install` cannot fail for reasons unrelated to the platform and the whole
pipeline finishes in seconds.

## Deploying it

1. Push this directory to a GitHub repository (or use it as the root directory of a monorepo import).
2. In DeployForge: **Import repository**, pick the branch, then **Deploy**.
3. Detection should produce:

   | Field           | Value          |
   | --------------- | -------------- |
   | Framework       | Node.js        |
   | Install command | `npm install`  |
   | Build command   | _(none)_       |
   | Start command   | `npm start`    |
   | Port            | 3000           |

   There is no lockfile, so detection chooses `npm install` rather than `npm ci`. That is intentional -
   it exercises the fallback path.

4. Set the health check path to `/healthz` in project settings (or leave `/`, both return 200).

## What it demonstrates

- **Binding correctly.** It listens on `0.0.0.0` and reads `PORT`, which DeployForge injects to match
  the port it published and probes. An app that hard codes a port or binds `127.0.0.1` fails its health
  check, and that is the most common first-deployment mistake.
- **Secret hygiene.** It logs the *names* of the environment variables it received, never the values -
  the same rule the platform applies to its own logs.
- **Graceful shutdown.** It handles `SIGTERM`, which is what makes retiring the previous container quick
  when a new deployment is promoted.

## Endpoints

| Path        | Purpose                                          |
| ----------- | ------------------------------------------------ |
| `/`         | HTML page showing the deployment metadata        |
| `/healthz`  | JSON health response used by the health check    |
| `/api/info` | Deployment metadata and environment variable names |
