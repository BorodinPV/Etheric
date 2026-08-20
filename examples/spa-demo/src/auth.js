import { AUTH_SERVER, CLIENT_ID, POST_LOGOUT_REDIRECT_URI, REDIRECT_URI, SCOPES } from './config';

const STORAGE_KEYS = {
  accessToken: 'etheric_access_token',
  refreshToken: 'etheric_refresh_token',
  idToken: 'etheric_id_token',
  scope: 'etheric_scope',
  codeVerifier: 'etheric_code_verifier',
  oauthState: 'etheric_oauth_state',
};

/** Refresh access token this many seconds before JWT exp. */
export const REFRESH_SKEW_SECONDS = 60;

export class SessionExpiredError extends Error {
  constructor(message = 'Session expired') {
    super(message);
    this.name = 'SessionExpiredError';
  }
}

let refreshInFlight = null;

function randomString(length) {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, (b) => (b % 36).toString(36)).join('');
}

function base64UrlEncode(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function sha256(input) {
  const data = new TextEncoder().encode(input);
  return crypto.subtle.digest('SHA-256', data);
}

export async function createPkcePair() {
  const verifier = randomString(64);
  const hash = await sha256(verifier);
  const challenge = base64UrlEncode(hash);
  return { verifier, challenge };
}

export function startRegistration() {
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    return_uri: window.location.origin + '/',
  });
  window.location.href = `${AUTH_SERVER}/register?${params.toString()}`;
}

export async function startLogin() {
  const state = randomString(32);
  const { verifier, challenge } = await createPkcePair();
  sessionStorage.setItem(STORAGE_KEYS.codeVerifier, verifier);
  sessionStorage.setItem(STORAGE_KEYS.oauthState, state);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });
  SCOPES.forEach((scope) => params.append('scope', scope));

  window.location.href = `${AUTH_SERVER}/authorize?${params.toString()}`;
}

export async function handleCallback(searchParams) {
  const error = searchParams.get('error');
  if (error) {
    throw new Error(searchParams.get('error_description') || error);
  }

  const code = searchParams.get('code');
  const state = searchParams.get('state');
  const expectedState = sessionStorage.getItem(STORAGE_KEYS.oauthState);
  if (!code || !state || !expectedState || state !== expectedState) {
    throw new Error('Invalid OAuth state or missing authorization code');
  }

  const codeVerifier = sessionStorage.getItem(STORAGE_KEYS.codeVerifier);
  if (!codeVerifier) {
    throw new Error('Missing PKCE code verifier');
  }

  sessionStorage.removeItem(STORAGE_KEYS.oauthState);
  sessionStorage.removeItem(STORAGE_KEYS.codeVerifier);

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: REDIRECT_URI,
    client_id: CLIENT_ID,
    code_verifier: codeVerifier,
  });

  const response = await fetch(`${AUTH_SERVER}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error_description || data.error || 'Token exchange failed');
  }

  storeTokens(data);
  return data;
}

export function storeTokens(data) {
  sessionStorage.setItem(STORAGE_KEYS.accessToken, data.access_token);
  if (data.refresh_token) {
    sessionStorage.setItem(STORAGE_KEYS.refreshToken, data.refresh_token);
  }
  if (data.id_token) {
    sessionStorage.setItem(STORAGE_KEYS.idToken, data.id_token);
  }
  if (data.scope) {
    sessionStorage.setItem(STORAGE_KEYS.scope, data.scope);
  }
}

export function clearTokens() {
  Object.values(STORAGE_KEYS).forEach((key) => sessionStorage.removeItem(key));
}

export function getStoredTokens() {
  return {
    accessToken: sessionStorage.getItem(STORAGE_KEYS.accessToken),
    refreshToken: sessionStorage.getItem(STORAGE_KEYS.refreshToken),
    idToken: sessionStorage.getItem(STORAGE_KEYS.idToken),
    scope: sessionStorage.getItem(STORAGE_KEYS.scope),
  };
}

export function decodeJwt(token) {
  if (!token) {
    return null;
  }
  const payload = token.split('.')[1];
  const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
  return JSON.parse(json);
}

export function isTokenExpired(token, skewSeconds = 0) {
  const claims = decodeJwt(token);
  if (!claims?.exp) {
    return true;
  }
  return Date.now() / 1000 >= claims.exp - skewSeconds;
}

export function hasValidSession() {
  const { accessToken, refreshToken, idToken } = getStoredTokens();
  if (!idToken) {
    return false;
  }
  if (refreshToken && !isTokenExpired(refreshToken)) {
    return true;
  }
  return Boolean(accessToken && !isTokenExpired(accessToken));
}

export function getMsUntilAccessTokenRefresh() {
  const { accessToken } = getStoredTokens();
  const claims = decodeJwt(accessToken);
  if (!claims?.exp) {
    return 0;
  }
  return Math.max(0, (claims.exp - REFRESH_SKEW_SECONDS) * 1000 - Date.now());
}

export function redirectToLogin() {
  clearTokens();
  window.location.href = '/';
}

export async function ensureValidAccessToken() {
  const { accessToken, refreshToken } = getStoredTokens();

  if (accessToken && !isTokenExpired(accessToken, REFRESH_SKEW_SECONDS)) {
    return accessToken;
  }

  if (!refreshToken || isTokenExpired(refreshToken)) {
    throw new SessionExpiredError();
  }

  if (!refreshInFlight) {
    refreshInFlight = refreshAccessToken().finally(() => {
      refreshInFlight = null;
    });
  }
  await refreshInFlight;
  return sessionStorage.getItem(STORAGE_KEYS.accessToken);
}

export async function refreshAccessToken() {
  const refreshToken = sessionStorage.getItem(STORAGE_KEYS.refreshToken);
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }

  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    refresh_token: refreshToken,
    client_id: CLIENT_ID,
  });

  const response = await fetch(`${AUTH_SERVER}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });

  const data = await response.json();
  if (!response.ok) {
    if (data.error === 'invalid_grant') {
      throw new SessionExpiredError(data.error_description || 'Session expired');
    }
    throw new Error(data.error_description || data.error || 'Refresh failed');
  }

  storeTokens(data);
  return data;
}

export async function introspectAccessToken(token) {
  const accessToken = token ?? (await ensureValidAccessToken());
  if (!accessToken) {
    throw new Error('No access token available');
  }

  const response = await fetch('/api/demo/introspect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token: accessToken, token_type_hint: 'access_token' }),
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error_description || data.error || 'Introspection failed');
  }
  return data;
}

async function revokeToken(token, tokenTypeHint) {
  const response = await fetch('/api/demo/revoke', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, token_type_hint: tokenTypeHint }),
  });

  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.error_description || data.error || 'Revocation failed');
  }
}

export async function revokeTokens() {
  const { accessToken, refreshToken } = getStoredTokens();
  if (refreshToken) {
    await revokeToken(refreshToken, 'refresh_token');
  }
  if (accessToken) {
    await revokeToken(accessToken, 'access_token');
  }
}

export async function logout() {
  try {
    await revokeTokens();
  } catch {
    // Best-effort revocation; continue logout
  }
  clearTokens();
  const params = new URLSearchParams({ redirect_uri: POST_LOGOUT_REDIRECT_URI });
  window.location.href = `${AUTH_SERVER}/logout?${params.toString()}`;
}
