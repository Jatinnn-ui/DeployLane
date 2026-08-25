'use strict';

/**
 * Fails at startup when DATABASE_URL is missing.
 *
 * This is a real failure, not a simulated one: the process validates its configuration, prints a
 * diagnostic to stderr and exits non-zero, exactly like an ORM or database client would. DeployForge
 * therefore sees a container that starts and immediately dies, fails the health check, captures the
 * container output and hands it to the analyzer.
 *
 * Set DATABASE_URL in the project's environment and redeploy, and the same code serves traffic.
 */

const http = require('node:http');

const REQUIRED_VARIABLES = ['DATABASE_URL'];

const missing = REQUIRED_VARIABLES.filter((name) => {
  const value = process.env[name];
  return value === undefined || value.trim() === '';
});

if (missing.length > 0) {
  for (const name of missing) {
    console.error(`FATAL: required environment variable ${name} is not set`);
  }
  console.error('Cannot initialise the database client without a connection string.');
  console.error('Add it in DeployForge under Project -> Environment, then redeploy.');
  process.exit(1);
}

// A connection string is present. Check its shape before trusting it - a wrong value produces a very
// different failure from a missing one, and the analyzer distinguishes the two.
const databaseUrl = process.env.DATABASE_URL;
if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(databaseUrl)) {
  console.error('FATAL: DATABASE_URL is set but is not a valid connection URL');
  console.error('Expected something like postgres://user:password@host:5432/database');
  process.exit(1);
}

const PORT = Number.parseInt(process.env.PORT ?? '3000', 10);

const server = http.createServer((request, response) => {
  if (request.url === '/healthz') {
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ status: 'ok', database: 'configured' }));
    return;
  }
  response.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
  response.end(
    'Configuration is valid and DATABASE_URL is set.\n' +
      'The value itself is never printed - not by this app, and not by DeployForge.\n',
  );
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`broken-env-app started successfully on port ${PORT}`);
  // Scheme only. Printing the whole URL would leak credentials into the deployment log.
  console.log(`database scheme: ${databaseUrl.split('://')[0]}`);
});

for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => server.close(() => process.exit(0)));
}
