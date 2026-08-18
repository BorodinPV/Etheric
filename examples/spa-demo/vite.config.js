import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const AUTH_SERVER = 'http://localhost:8080';
const DEMO_CLIENT_ID = 'test-client';
const DEMO_CLIENT_SECRET = 'secret';

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (chunk) => {
      data += chunk;
    });
    req.on('end', () => {
      try {
        resolve(data ? JSON.parse(data) : {});
      } catch (err) {
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

function demoAuthProxy() {
  const basicAuth = Buffer.from(`${DEMO_CLIENT_ID}:${DEMO_CLIENT_SECRET}`).toString('base64');

  async function forward(path, req, res) {
    if (req.method !== 'POST') {
      res.statusCode = 405;
      res.end('Method Not Allowed');
      return;
    }

    try {
      const body = await readJsonBody(req);
      const { token, token_type_hint: tokenTypeHint } = body;

      if (!token) {
        res.statusCode = 400;
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify({ error: 'invalid_request', error_description: 'token is required' }));
        return;
      }

      const params = new URLSearchParams({ token });
      if (tokenTypeHint) {
        params.set('token_type_hint', tokenTypeHint);
      }

      const response = await fetch(`${AUTH_SERVER}${path}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
        body: params,
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

  return {
    name: 'demo-auth-proxy',
    configureServer(server) {
      server.middlewares.use('/api/demo/introspect', (req, res) => forward('/introspect', req, res));
      server.middlewares.use('/api/demo/revoke', (req, res) => forward('/revoke', req, res));
    },
  };
}

export default defineConfig({
  plugins: [react(), demoAuthProxy()],
  server: {
    port: 5173,
    strictPort: true,
  },
});
