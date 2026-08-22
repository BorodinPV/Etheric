/**
 * Confidential-client BFF for Etheric.
 * client_secret never leaves this process — the browser only talks to this server.
 */
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = path.join(__dirname, 'public');

const AUTH_SERVER = process.env.ETHERIC_URL || 'http://localhost:8080';
const CLIENT_ID = process.env.CLIENT_ID || 'confidential-demo';
const CLIENT_SECRET = process.env.CLIENT_SECRET || 'confidential-secret';
const APP_ORIGIN = process.env.APP_ORIGIN || 'http://localhost:5174';
const REDIRECT_URI = process.env.REDIRECT_URI || `${APP_ORIGIN}/callback`;
const POST_LOGOUT_REDIRECT_URI = process.env.POST_LOGOUT_REDIRECT_URI || `${APP_ORIGIN}/`;
const PORT = Number.parseInt(process.env.PORT || '5174', 10);
const SCOPES = ['openid', 'profile', 'email'];
const COOKIE_NAME = 'CONFIDENTIAL_SESSION';
const REFRESH_SKEW_MS = 60_000;

const sessions = new Map();

function randomId() {
  return crypto.randomBytes(24).toString('hex');
}

function basicAuth() {
  return `Basic ${Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`).toString('base64')}`;
}

function parseCookies(req) {
  const header = req.headers.cookie || '';
  const out = {};
  for (const part of header.split(';')) {
    const idx = part.indexOf('=');
    if (idx > 0) {
      out[part.slice(0, idx).trim()] = decodeURIComponent(part.slice(idx + 1).trim());
    }
  }
  return out;
}

function sessionCookie(id, clear = false) {
  const parts = [
    `${COOKIE_NAME}=${clear ? '' : id}`,
    'Path=/',
    'HttpOnly',
    'SameSite=Lax',
  ];
  if (clear) {
    parts.push('Max-Age=0');
  }
  return parts.join('; ');
}

function getSession(req) {
  const id = parseCookies(req)[COOKIE_NAME];
  if (!id) {
    return { id: null, data: null };
  }
  return { id, data: sessions.get(id) || null };
}

function requireSameOrigin(req, res) {
  const origin = req.headers.origin;
  if (!origin) {
    return true;
  }
  if (origin === APP_ORIGIN) {
    return true;
  }
  json(res, 403, { error: 'forbidden', error_description: 'Cross-origin request blocked' });
  return false;
}

function send(res, status, headers, body) {
  res.writeHead(status, headers);
  res.end(body);
}

function redirect(res, location, extraHeaders = {}) {
  send(res, 302, { Location: location, ...extraHeaders }, '');
}

function json(res, status, payload, extraHeaders = {}) {
  send(res, status, {
    'Content-Type': 'application/json; charset=utf-8',
    ...extraHeaders,
  }, JSON.stringify(payload));
}

function decodeJwt(token) {
  if (!token) {
    return null;
  }
  const parts = token.split('.');
  if (parts.length < 2) {
    return null;
  }
  try {
    return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
  } catch {
    return null;
  }
}

function sessionView(data) {
  if (!data?.tokens) {
    return { authenticated: false };
  }
  return {
    authenticated: true,
    clientId: CLIENT_ID,
    scope: data.tokens.scope || '',
    expiresAt: data.expiresAt || null,
    idClaims: decodeJwt(data.tokens.id_token),
    accessClaims: decodeJwt(data.tokens.access_token),
    tokensInBrowser: false,
  };
}

async function ethericForm(pathname, body, { auth = true } = {}) {
  const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
  if (auth) {
    headers.Authorization = basicAuth();
  }
  const response = await fetch(`${AUTH_SERVER}${pathname}`, {
    method: 'POST',
    headers,
    body: new URLSearchParams(body),
  });
  const text = await response.text();
  let payload = {};
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = { raw: text };
    }
  }
  return { ok: response.ok, status: response.status, payload };
}

function storeTokens(sessionId, tokens) {
  const expiresIn = Number.parseInt(tokens.expires_in, 10);
  const data = sessions.get(sessionId) || {};
  data.tokens = tokens;
  data.expiresAt = Number.isFinite(expiresIn)
    ? Date.now() + expiresIn * 1000
    : Date.now() + 3600_000;
  sessions.set(sessionId, data);
}

async function refreshIfNeeded(sessionId, data) {
  if (!data?.tokens?.refresh_token) {
    return data;
  }
  if (data.expiresAt && data.expiresAt - Date.now() > REFRESH_SKEW_MS) {
    return data;
  }
  const result = await ethericForm('/token', {
    grant_type: 'refresh_token',
    refresh_token: data.tokens.refresh_token,
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
  });
  if (!result.ok) {
    sessions.delete(sessionId);
    return null;
  }
  storeTokens(sessionId, result.payload);
  return sessions.get(sessionId);
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
};

function serveStatic(req, res) {
  const url = new URL(req.url, APP_ORIGIN);
  const relative = (url.pathname === '/' ? '/index.html' : url.pathname).replace(/^\/+/, '');
  if (relative.includes('..')) {
    send(res, 403, { 'Content-Type': 'text/plain' }, 'Forbidden');
    return;
  }
  const filePath = path.join(PUBLIC_DIR, relative);
  fs.readFile(filePath, (err, content) => {
    if (err) {
      send(res, 404, { 'Content-Type': 'text/plain' }, 'Not found');
      return;
    }
    const type = MIME[path.extname(filePath)] || 'application/octet-stream';
    send(res, 200, { 'Content-Type': type }, content);
  });
}

async function handleLogin(req, res) {
  const sessionId = getSession(req).id || randomId();
  const state = randomId();
  const nonce = randomId();
  const existing = sessions.get(sessionId) || {};
  existing.state = state;
  existing.nonce = nonce;
  sessions.set(sessionId, existing);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    state,
    nonce,
  });
  for (const scope of SCOPES) {
    params.append('scope', scope);
  }
  redirect(res, `${AUTH_SERVER}/authorize?${params}`, {
    'Set-Cookie': sessionCookie(sessionId),
  });
}

function handleRegister(_req, res) {
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    return_uri: POST_LOGOUT_REDIRECT_URI,
  });
  redirect(res, `${AUTH_SERVER}/register?${params}`);
}

async function handleCallback(req, res) {
  const url = new URL(req.url, APP_ORIGIN);
  const error = url.searchParams.get('error');
  if (error) {
    redirect(res, `/?error=${encodeURIComponent(error)}`);
    return;
  }

  const { id, data } = getSession(req);
  const code = url.searchParams.get('code');
  const state = url.searchParams.get('state');
  if (!id || !data?.state || !code || state !== data.state) {
    redirect(res, '/?error=invalid_state');
    return;
  }

  const result = await ethericForm('/token', {
    grant_type: 'authorization_code',
    code,
    redirect_uri: REDIRECT_URI,
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
  });
  if (!result.ok) {
    const description = result.payload.error || 'token_error';
    redirect(res, `/?error=${encodeURIComponent(description)}`);
    return;
  }

  if (result.payload.id_token && data.nonce) {
    const claims = decodeJwt(result.payload.id_token);
    if (!claims || claims.nonce !== data.nonce) {
      redirect(res, '/?error=nonce_mismatch');
      return;
    }
  }

  delete data.state;
  delete data.nonce;
  storeTokens(id, result.payload);
  redirect(res, '/');
}

async function handleSession(req, res) {
  const { id, data } = getSession(req);
  if (!id || !data?.tokens) {
    json(res, 200, { authenticated: false });
    return;
  }
  const fresh = await refreshIfNeeded(id, data);
  if (!fresh) {
    json(res, 200, { authenticated: false }, { 'Set-Cookie': sessionCookie('', true) });
    return;
  }
  json(res, 200, sessionView(fresh));
}

async function handleIntrospect(req, res) {
  if (!requireSameOrigin(req, res)) {
    return;
  }
  const { id, data } = getSession(req);
  if (!id || !data?.tokens?.access_token) {
    json(res, 401, { error: 'login_required' });
    return;
  }
  const fresh = await refreshIfNeeded(id, data);
  if (!fresh) {
    json(res, 401, { error: 'login_required' }, { 'Set-Cookie': sessionCookie('', true) });
    return;
  }
  const result = await ethericForm('/introspect', {
    token: fresh.tokens.access_token,
    token_type_hint: 'access_token',
  });
  json(res, result.status, result.payload);
}

async function handleRefresh(req, res) {
  if (!requireSameOrigin(req, res)) {
    return;
  }
  const { id, data } = getSession(req);
  if (!id || !data?.tokens?.refresh_token) {
    json(res, 401, { error: 'login_required' });
    return;
  }
  const result = await ethericForm('/token', {
    grant_type: 'refresh_token',
    refresh_token: data.tokens.refresh_token,
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
  });
  if (!result.ok) {
    sessions.delete(id);
    json(res, result.status, result.payload, { 'Set-Cookie': sessionCookie('', true) });
    return;
  }
  storeTokens(id, result.payload);
  json(res, 200, sessionView(sessions.get(id)));
}

async function handleLogout(req, res) {
  if (!requireSameOrigin(req, res)) {
    return;
  }
  const { id, data } = getSession(req);
  if (data?.tokens) {
    const tokens = [data.tokens.access_token, data.tokens.refresh_token].filter(Boolean);
    for (const token of tokens) {
      await ethericForm('/revoke', { token }).catch(() => undefined);
    }
  }
  if (id) {
    sessions.delete(id);
  }
  const logoutUrl = `${AUTH_SERVER}/logout?${new URLSearchParams({
    redirect_uri: POST_LOGOUT_REDIRECT_URI,
    client_id: CLIENT_ID,
  })}`;
  json(res, 200, { redirect: logoutUrl }, { 'Set-Cookie': sessionCookie('', true) });
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, APP_ORIGIN);
    if (req.method === 'GET' && url.pathname === '/login') {
      await handleLogin(req, res);
      return;
    }
    if (req.method === 'GET' && url.pathname === '/register') {
      handleRegister(req, res);
      return;
    }
    if (req.method === 'GET' && url.pathname === '/callback') {
      await handleCallback(req, res);
      return;
    }
    if (req.method === 'GET' && url.pathname === '/api/session') {
      await handleSession(req, res);
      return;
    }
    if (req.method === 'POST' && url.pathname === '/api/introspect') {
      await handleIntrospect(req, res);
      return;
    }
    if (req.method === 'POST' && url.pathname === '/api/refresh') {
      await handleRefresh(req, res);
      return;
    }
    if (req.method === 'POST' && url.pathname === '/api/logout') {
      await handleLogout(req, res);
      return;
    }
    if (req.method === 'GET') {
      serveStatic(req, res);
      return;
    }
    send(res, 405, { 'Content-Type': 'text/plain' }, 'Method Not Allowed');
  } catch (err) {
    json(res, 500, { error: 'server_error', error_description: err.message });
  }
});

server.listen(PORT, () => {
  console.log(`Confidential demo BFF on ${APP_ORIGIN}`);
  console.log(`  Etheric: ${AUTH_SERVER}`);
  console.log(`  client_id: ${CLIENT_ID} (secret stays in this process)`);
});
