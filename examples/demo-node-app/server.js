'use strict';

/**
 * Minimal HTTP service for demonstrating a successful DeployForge deployment.
 *
 * Deliberately dependency free, so `npm install` cannot fail for reasons unrelated to the platform,
 * and the whole deployment finishes in seconds.
 *
 * Two things it demonstrates that matter for the platform:
 *
 *  1. It binds `0.0.0.0` and reads `PORT`. DeployForge injects PORT and probes the port it published,
 *     so an app that hard codes a port or binds 127.0.0.1 fails its health check - which is the single
 *     most common deployment mistake.
 *  2. It prints the *names* of the environment variables it received, never the values. Same rule the
 *     platform follows.
 */

const http = require('node:http');

const PORT = Number.parseInt(process.env.PORT ?? '3000', 10);
const HOST = '0.0.0.0';
const startedAt = new Date();

const deployment = {
  project: process.env.DEPLOYFORGE_PROJECT ?? 'local',
  deploymentNumber: process.env.DEPLOYFORGE_DEPLOYMENT_NUMBER ?? 'n/a',
  commit: process.env.DEPLOYFORGE_COMMIT_SHA ?? 'unknown',
  branch: process.env.DEPLOYFORGE_BRANCH ?? 'unknown',
  managed: process.env.DEPLOYFORGE === 'true',
};

/** Names only - printing a value would leak a secret into the deployment log. */
const variableNames = Object.keys(process.env)
  .filter((name) => !name.startsWith('npm_') && !['PATH', 'HOME', 'HOSTNAME', 'PWD'].includes(name))
  .sort();

const server = http.createServer((request, response) => {
  const url = new URL(request.url ?? '/', `http://${request.headers.host ?? 'localhost'}`);

  if (url.pathname === '/healthz') {
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(
      JSON.stringify({
        status: 'ok',
        uptimeSeconds: Math.round(process.uptime()),
        startedAt: startedAt.toISOString(),
      }),
    );
    return;
  }

  if (url.pathname === '/api/info') {
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(
      JSON.stringify({
        deployment,
        node: process.version,
        environmentVariableNames: variableNames,
      }),
    );
    return;
  }

  response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  response.end(`<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Deployed by DeployForge AI</title>
    <style>
      :root { color-scheme: dark; }
      body {
        margin: 0; min-height: 100vh; display: grid; place-items: center;
        background: #08090b; color: #f2f3f5;
        font-family: ui-sans-serif, system-ui, -apple-system, sans-serif;
      }
      .card {
        width: min(38rem, calc(100vw - 2rem));
        border: 1px solid #1e2128; border-radius: 12px; background: #0e0f13; padding: 2rem;
      }
      h1 { margin: 0 0 .25rem; font-size: 1.25rem; letter-spacing: -0.01em; }
      p  { margin: 0; color: #a4a9b4; font-size: .875rem; }
      dl { margin: 1.5rem 0 0; display: grid; grid-template-columns: auto 1fr; gap: .5rem 1rem; font-size: .8125rem; }
      dt { color: #6d737f; text-transform: uppercase; letter-spacing: .08em; font-size: .6875rem; }
      dd { margin: 0; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
      .dot { display: inline-block; width: .5rem; height: .5rem; border-radius: 50%; background: #22c55e; margin-right: .5rem; }
    </style>
  </head>
  <body>
    <main class="card">
      <h1><span class="dot"></span>Deployed by DeployForge AI</h1>
      <p>This container was built from a GitHub repository, started with resource limits and health checked before receiving traffic.</p>
      <dl>
        <dt>Project</dt><dd>${escapeHtml(deployment.project)}</dd>
        <dt>Deployment</dt><dd>#${escapeHtml(deployment.deploymentNumber)}</dd>
        <dt>Branch</dt><dd>${escapeHtml(deployment.branch)}</dd>
        <dt>Commit</dt><dd>${escapeHtml(deployment.commit.slice(0, 7))}</dd>
        <dt>Node</dt><dd>${escapeHtml(process.version)}</dd>
        <dt>Port</dt><dd>${PORT}</dd>
        <dt>Uptime</dt><dd>${Math.round(process.uptime())}s</dd>
      </dl>
    </main>
  </body>
</html>`);
});

server.listen(PORT, HOST, () => {
  console.log(`demo-node-app listening on http://${HOST}:${PORT}`);
  console.log(`health endpoint: /healthz`);
  console.log(`received ${variableNames.length} environment variables (names only): ${variableNames.join(', ')}`);
});

// Containers are stopped with SIGTERM. Exiting cleanly is what makes a zero-downtime switch quick.
for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    console.log(`${signal} received, shutting down`);
    server.close(() => process.exit(0));
  });
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => {
    switch (character) {
      case '&':
        return '&amp;';
      case '<':
        return '&lt;';
      case '>':
        return '&gt;';
      case '"':
        return '&quot;';
      default:
        return '&#39;';
    }
  });
}
