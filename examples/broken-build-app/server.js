'use strict';

/**
 * Server for broken-build-app. It never runs in the broken state, because the image build fails first.
 *
 * Add `slugify` to `dependencies` (or delete the import in build.js) and redeploy, and this serves.
 */

const http = require('node:http');

const PORT = Number.parseInt(process.env.PORT ?? '3000', 10);

http
  .createServer((request, response) => {
    if (request.url === '/healthz') {
      response.writeHead(200, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify({ status: 'ok' }));
      return;
    }
    response.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('Build succeeded and the application is serving.\n');
  })
  .listen(PORT, '0.0.0.0', () => {
    console.log(`broken-build-app listening on ${PORT}`);
  });
