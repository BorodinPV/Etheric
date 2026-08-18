/**
 * Etheric load test (k6)
 *
 * Prerequisites:
 *   - Etheric running (dev seed: test-client / secret, user / password)
 *   - Disable rate limit: .\scripts\dev.ps1 -DisableRateLimit
 *
 * Env: BASE_URL, VUS, DURATION, SCENARIO=all|refresh|introspect|public|authorize
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CLIENT_ID = __ENV.CLIENT_ID || 'test-client';
const CLIENT_SECRET = __ENV.CLIENT_SECRET || 'secret';
const REDIRECT_URI = __ENV.REDIRECT_URI || 'http://localhost:8080/callback';
const USERNAME = __ENV.USERNAME || 'user';
const PASSWORD = __ENV.PASSWORD || 'password';

const VUS = Math.max(1, parseInt(__ENV.VUS || '10', 10));
const DURATION = __ENV.DURATION || '30s';
const SCENARIO = (__ENV.SCENARIO || 'all').toLowerCase();
const INTROSPECT_VUS = Math.max(1, Math.min(VUS, 5));

const refreshErrors = new Rate('etheric_refresh_errors');
const refreshDuration = new Trend('etheric_refresh_duration', true);
const rateLimited = new Counter('etheric_rate_limited');

const vuTokens = {};

function enabled(name) {
  return SCENARIO === 'all' || SCENARIO === name;
}

function buildOptions() {
  const scenarios = {};
  if (enabled('refresh')) {
    scenarios.token_refresh = {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: 'refreshToken',
    };
  }
  if (enabled('introspect')) {
    scenarios.introspect = {
      executor: 'constant-vus',
      vus: INTROSPECT_VUS,
      duration: DURATION,
      exec: 'introspectToken',
    };
  }
  if (enabled('public')) {
    scenarios.public_read = {
      executor: 'constant-arrival-rate',
      rate: Math.max(10, VUS * 5),
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(VUS, 10),
      maxVUs: Math.max(VUS * 3, 30),
      exec: 'publicEndpoints',
    };
  }
  if (enabled('authorize')) {
    scenarios.authorize = {
      executor: 'constant-arrival-rate',
      rate: Math.max(5, VUS),
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(VUS, 5),
      maxVUs: Math.max(VUS * 2, 20),
      exec: 'authorizeStart',
    };
  }
  if (Object.keys(scenarios).length === 0) {
    throw new Error(`Unknown SCENARIO="${SCENARIO}". Use all|refresh|introspect|public|authorize`);
  }
  return {
    setupTimeout: '180s',
    scenarios,
    thresholds: {
      http_req_failed: ['rate<0.05'],
      'http_req_failed{endpoint:token_refresh}': [
        SCENARIO === 'all' ? 'rate<0.25' : 'rate<0.03',
      ],
      'http_req_failed{endpoint:introspect}': ['rate<0.02'],
      'http_req_failed{endpoint:authorize}': ['rate<0.02'],
      'http_req_failed{endpoint:jwks}': ['rate<0.01'],
      http_req_duration: ['p(95)<3000'],
    },
  };
}

export const options = buildOptions();

function extractCsrf(html) {
  const match = html.match(/name="csrf_token"\s+value="([^"]+)"/);
  return match ? match[1] : null;
}

function absoluteUrl(base, location) {
  if (!location) return null;
  if (location.startsWith('http://') || location.startsWith('https://')) {
    return location;
  }
  if (location.startsWith('/')) {
    return `${base}${location}`;
  }
  return `${base}/${location}`;
}

function parseCodeFromLocation(location) {
  if (!location) return null;
  const match = location.match(/[?&]code=([^&]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function formatHttpError(res, context) {
  if (res.status === 0) {
    return (
      `${context}: no response from ${BASE_URL} (connection refused or unreachable from k6). ` +
      'Ensure Etheric is running and Docker can reach the host (host.docker.internal:8080).'
    );
  }
  return `${context} (HTTP ${res.status}, url ${res.url})`;
}

function waitForServer() {
  for (let attempt = 1; attempt <= 30; attempt += 1) {
    const res = http.get(`${BASE_URL}/health/live`, {
      timeout: '3s',
      tags: { phase: 'setup' },
    });
    if (res.status === 200) {
      return;
    }
    if (res.status === 0 && attempt === 1) {
      console.warn(`Waiting for ${BASE_URL} ... (attempt ${attempt}/30)`);
    }
    sleep(1);
  }
  throw new Error(
    `Cannot reach ${BASE_URL}/health/live from k6 after 30s. ` +
    'Start Etheric: .\\scripts\\dev.ps1 -DisableRateLimit',
  );
}

function obtainTokensOnce() {
  const jar = http.cookieJar();
  const state = `k6-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  const authorizeUrl =
    `${BASE_URL}/authorize?response_type=code` +
    `&client_id=${encodeURIComponent(CLIENT_ID)}` +
    `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
    `&state=${encodeURIComponent(state)}` +
    `&scope=openid&scope=profile`;

  let res = http.get(authorizeUrl, { redirects: 0, jar, tags: { phase: 'setup' }, timeout: '15s' });

  if (res.status === 0) {
    throw new Error(formatHttpError(res, 'OAuth authorize'));
  }

  for (let hop = 0; hop < 15; hop += 1) {
    if (res.status >= 300 && res.status < 400) {
      const next = absoluteUrl(BASE_URL, res.headers.Location);
      const code = parseCodeFromLocation(next);
      if (code) {
        return exchangeCode(code);
      }
      res = http.get(next, { redirects: 0, jar, tags: { phase: 'setup' } });
      continue;
    }

    if (res.status === 200 && res.url && res.url.includes('/login')) {
      const csrf = extractCsrf(res.body);
      if (!csrf) {
        throw new Error('Login page missing CSRF token');
      }
      res = http.post(
        `${BASE_URL}/login`,
        {
          username: USERNAME,
          password: PASSWORD,
          state: state,
          csrf_token: csrf,
        },
        {
          redirects: 0,
          jar,
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          tags: { phase: 'setup' },
        },
      );
      continue;
    }

    if (res.status === 200 && res.url && res.url.includes('/consent')) {
      const csrf = extractCsrf(res.body);
      if (!csrf) {
        throw new Error('Consent page missing CSRF token');
      }
      res = http.post(
        `${BASE_URL}/consent`,
        {
          action: 'approve',
          state: state,
          csrf_token: csrf,
        },
        {
          redirects: 0,
          jar,
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          tags: { phase: 'setup' },
        },
      );
      continue;
    }

    break;
  }

  throw new Error(formatHttpError(res, 'OAuth setup failed'));
}

function obtainTokens() {
  const maxAttempts = 3;
  let lastError = null;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      return obtainTokensOnce();
    } catch (err) {
      lastError = err;
      if (attempt < maxAttempts) {
        sleep(1);
      }
    }
  }
  throw lastError;
}

function exchangeCode(code) {
  const res = http.post(
    `${BASE_URL}/token`,
    {
      grant_type: 'authorization_code',
      code: code,
      redirect_uri: REDIRECT_URI,
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
    },
    {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      tags: { phase: 'setup' },
    },
  );

  if (res.status !== 200) {
    throw new Error(`Token exchange failed: ${res.status} ${res.body}`);
  }

  const body = JSON.parse(res.body);
  return {
    access: body.access_token,
    refresh: body.refresh_token,
  };
}

export function setup() {
  if (!enabled('refresh') && !enabled('introspect')) {
    return { refreshPool: [], introspectPool: [] };
  }

  waitForServer();

  const refreshPool = [];
  const introspectPool = [];

  if (enabled('refresh')) {
    for (let i = 0; i < VUS; i += 1) {
      refreshPool.push(obtainTokens());
      sleep(0.25);
    }
  }

  if (enabled('introspect')) {
    for (let i = 0; i < INTROSPECT_VUS; i += 1) {
      introspectPool.push(obtainTokens());
      sleep(0.25);
    }
  }

  return { refreshPool, introspectPool };
}

function tokenForVu(data, poolKind) {
  const key = `${poolKind}:${__VU}`;
  if (!vuTokens[key]) {
    const pool = poolKind === 'refresh' ? data.refreshPool : data.introspectPool;
    if (!pool || pool.length === 0) {
      return { access: null, refresh: null };
    }
    const idx = (__VU - 1) % pool.length;
    vuTokens[key] = { access: pool[idx].access, refresh: pool[idx].refresh };
  }
  return vuTokens[key];
}

function postRefresh(refreshTokenValue) {
  return http.post(
    `${BASE_URL}/token`,
    {
      grant_type: 'refresh_token',
      refresh_token: refreshTokenValue,
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
    },
    {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      tags: { endpoint: 'token_refresh' },
      timeout: '15s',
    },
  );
}

function isRetryableStatus(status) {
  return status === 0 || status === 502 || status === 503 || status === 504;
}

function isInvalidGrant(res) {
  if (res.status !== 400) {
    return false;
  }
  try {
    return JSON.parse(res.body).error === 'invalid_grant';
  } catch (e) {
    return false;
  }
}

function applyTokenResponse(store, res) {
  const body = JSON.parse(res.body);
  store.refresh = body.refresh_token;
  store.access = body.access_token;
}

export function refreshToken(data) {
  const store = tokenForVu(data, 'refresh');
  if (!store.refresh) {
    return;
  }

  let res = postRefresh(store.refresh);

  if (isRetryableStatus(res.status)) {
    sleep(0.15);
    res = postRefresh(store.refresh);
  }

  if (isInvalidGrant(res)) {
    try {
      const fresh = obtainTokens();
      store.refresh = fresh.refresh;
      store.access = fresh.access;
      res = postRefresh(store.refresh);
    } catch (e) {
      // keep original error response for metrics
    }
  }

  if (res.status === 429) {
    rateLimited.add(1);
  }

  const ok = check(res, {
    'refresh 200': (r) => r.status === 200,
    'rotated refresh_token': (r) => {
      try {
        return JSON.parse(r.body).refresh_token;
      } catch (e) {
        return false;
      }
    },
  });

  refreshErrors.add(!ok);
  refreshDuration.add(res.timings.duration);

  if (ok) {
    applyTokenResponse(store, res);
  }

  sleep(0.1);
}

export function introspectToken(data) {
  const store = tokenForVu(data, 'introspect');
  if (!store.access) {
    return;
  }

  const res = http.post(
    `${BASE_URL}/introspect`,
    {
      token: store.access,
      token_type_hint: 'access_token',
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
    },
    {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      tags: { endpoint: 'introspect' },
    },
  );

  if (res.status === 429) {
    rateLimited.add(1);
  }

  check(res, {
    'introspect 200': (r) => r.status === 200,
    'introspect active': (r) => {
      try {
        return JSON.parse(r.body).active === true;
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.05);
}

export function publicEndpoints() {
  const jwks = http.get(`${BASE_URL}/.well-known/jwks.json`, { tags: { endpoint: 'jwks' } });
  check(jwks, { 'jwks 200': (r) => r.status === 200 });

  const live = http.get(`${BASE_URL}/health/live`, { tags: { endpoint: 'health_live' } });
  check(live, { 'live 200': (r) => r.status === 200 });
}

export function authorizeStart() {
  const state = `load-${__VU}-${__ITER}-${Date.now()}`;
  const url =
    `${BASE_URL}/authorize?response_type=code` +
    `&client_id=${encodeURIComponent(CLIENT_ID)}` +
    `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
    `&state=${encodeURIComponent(state)}` +
    `&scope=openid`;

  const res = http.get(url, { redirects: 0, tags: { endpoint: 'authorize' } });
  if (res.status === 429) {
    rateLimited.add(1);
  }
  check(res, {
    'authorize redirect': (r) => r.status === 302 || r.status === 303,
  });
}

function metricRate(data, name) {
  const metric = data.metrics[name];
  if (!metric || !metric.values) {
    return 0;
  }
  return metric.values.rate ?? 0;
}

export function handleSummary(data) {
  const p95 = data.metrics.http_req_duration?.values?.['p(95)'] ?? 0;
  const rps = data.metrics.http_reqs?.values?.rate ?? 0;
  const failed = data.metrics.http_req_failed?.values?.rate ?? 0;
  const rateLimitHits = data.metrics.etheric_rate_limited?.values?.count ?? 0;

  const lines = [
    '',
    '=== Etheric load test summary ===',
    `  scenarios : ${SCENARIO}`,
    `  target    : ${BASE_URL}`,
    `  duration  : ${DURATION}, VUs=${VUS}`,
    `  setup     : ${enabled('refresh') ? VUS : 0} refresh + ${enabled('introspect') ? INTROSPECT_VUS : 0} introspect tokens`,
    `  http RPS  : ${rps.toFixed(1)}`,
    `  p95       : ${p95.toFixed(1)} ms`,
    `  failed    : ${(failed * 100).toFixed(2)}%`,
    `  429 hits  : ${rateLimitHits}`,
    '',
    '  failed by endpoint:',
    `    token_refresh : ${(metricRate(data, 'http_req_failed{endpoint:token_refresh}') * 100).toFixed(2)}%`,
    `    introspect    : ${(metricRate(data, 'http_req_failed{endpoint:introspect}') * 100).toFixed(2)}%`,
    `    authorize     : ${(metricRate(data, 'http_req_failed{endpoint:authorize}') * 100).toFixed(2)}%`,
    `    jwks          : ${(metricRate(data, 'http_req_failed{endpoint:jwks}') * 100).toFixed(2)}%`,
    '',
    'Tip: .\\scripts\\dev.ps1 -DisableRateLimit',
    '      For cleaner refresh metrics: -Scenario refresh',
    '',
  ];

  return { stdout: lines.join('\n') };
}
