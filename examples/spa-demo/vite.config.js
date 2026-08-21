import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const AUTH_SERVER = 'http://localhost:8080';
const DEMO_CLIENT_ID = 'test-client';
const DEMO_CLIENT_SECRET = 'secret';

function readRequestBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => {
      data += chunk;
    });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

/**
 * Dev BFF: forwards SPA token requests to RFC 7662 /introspect and RFC 7009 /revoke
 * with confidential client Basic Auth (secret stays on the Node dev server, not in the browser bundle).
 */
function oauthBffProxy() {
  const basicAuth = Buffer.from(`${DEMO_CLIENT_ID}:${DEMO_CLIENT_SECRET}`).toString('base64');

  async function forward(standardPath, req, res) {
    if (req.method !== 'POST') {
      res.statusCode = 405;
      res.end('Method Not Allowed');
      return;
    }

    try {
      const rawBody = await readRequestBody(req);
      const params = new URLSearchParams(rawBody);
      const token = params.get('token');

      if (!token) {
        res.statusCode = 400;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'invalid_request', error_description: 'token is required' }));
        return;
      }

      const response = await fetch(`${AUTH_SERVER}${standardPath}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
        body: params.toString(),
      });

      const text = await response.text();
      res.statusCode = response.status;
      res.setHeader('Content-Type', response.headers.get('content-type') || 'application/json');
      res.end(text);
    } catch (err) {
      res.statusCode = 502;
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify({ error: 'proxy_error', error_description: err.message }));
    }
  }

  function mount(server) {
    server.middlewares.use('/api/oauth/introspect', (req, res) => forward('/introspect', req, res));
    server.middlewares.use('/api/oauth/revoke', (req, res) => forward('/revoke', req, res));
  }

  return {
    name: 'oauth-bff-proxy',
    configureServer(server) {
      mount(server);
    },
    configurePreviewServer(server) {
      mount(server);
    },
  };
}

export default defineConfig({
  plugins: [react(), oauthBffProxy()],
  server: {
    port: 5173,
    strictPort: true,
  },
});
