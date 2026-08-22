import { AUTH_SERVER, CLIENT_ID, POST_LOGOUT_REDIRECT_URI, REDIRECT_URI, SCOPES } from './config';

const STORAGE_KEYS = {
  accessToken: 'etheric_access_token',
  accessTokenExpiresAt: 'etheric_access_token_expires_at',
  refreshToken: 'etheric_refresh_token',
  idToken: 'etheric_id_token',
  scope: 'etheric_scope',
  codeVerifier: 'etheric_code_verifier',
  oauthState: 'etheric_oauth_state',
  oauthNonce: 'etheric_oauth_nonce',
};

/** Broadcast logout to other tabs (localStorage storage event). */
const LOGOUT_BROADCAST_KEY = 'etheric_logout_at';

/** Refresh access token this many seconds before expires_at. */
export const REFRESH_SKEW_SECONDS = 60;

const OAUTH_ERROR_MESSAGES = {
  invalid_grant:
    'Сессия истекла или код авторизации уже использован. / Session expired or authorization code already used.',
  access_denied: 'Доступ запрещён. / Access denied.',
  invalid_scope:
    'Запрошенные scope не разрешены для этого клиента. / Requested scopes are not allowed for this client.',
  temporarily_unavailable:
    'Сервис временно недоступен. Попробуйте позже. / Service temporarily unavailable. Please try again later.',
  invalid_request: 'Некорректный запрос OAuth. / Invalid OAuth request.',
  unauthorized_client:
    'Клиент не авторизован для этого запроса. / Client is not authorized for this request.',
  unsupported_grant_type: 'Неподдерживаемый grant type. / Unsupported grant type.',
  invalid_client: 'Неизвестный или неверный клиент. / Unknown or invalid client.',
  server_error: 'Ошибка сервера авторизации. / Authorization server error.',
  interaction_required:
    'Требуется повторный вход. / User interaction required — please sign in again.',
  login_required: 'Требуется вход. / Login required.',
};

/** Map OAuth error codes to user-friendly bilingual (RU/EN) messages. */
export function mapOAuthError(error, description) {
  if (!error) {
    return description || 'Unknown OAuth error';
  }
  const mapped = OAUTH_ERROR_MESSAGES[error];
  if (mapped) {
    return mapped;
  }
  if (description) {
    return `${description} (${error})`;
  }
  return error;
}

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
    binary += String.fromCodePoint(b);
  });
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
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
  const nonce = randomString(32);
  const { verifier, challenge } = await createPkcePair();
  sessionStorage.setItem(STORAGE_KEYS.codeVerifier, verifier);
  sessionStorage.setItem(STORAGE_KEYS.oauthState, state);
  sessionStorage.setItem(STORAGE_KEYS.oauthNonce, nonce);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    state,
    nonce,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });
  SCOPES.forEach((scope) => params.append('scope', scope));

  window.location.href = `${AUTH_SERVER}/authorize?${params.toString()}`;
}

export async function handleCallback(searchParams) {
  const error = searchParams.get('error');
  if (error) {
    throw new Error(mapOAuthError(error, searchParams.get('error_description')));
  }

  const code = searchParams.get('code');
  const state = searchParams.get('state');
  const expectedState = sessionStorage.getItem(STORAGE_KEYS.oauthState);
  if (!code || !state || !expectedState || state !== expectedState) {
    throw new Error('Invalid OAuth state or missing authorization code');
  }

  const codeVerifier = sessionStorage.getItem(STORAGE_KEYS.codeVerifier);
  const expectedNonce = sessionStorage.getItem(STORAGE_KEYS.oauthNonce);
  if (!codeVerifier) {
    throw new Error('Missing PKCE code verifier');
  }

  sessionStorage.removeItem(STORAGE_KEYS.oauthState);
  sessionStorage.removeItem(STORAGE_KEYS.codeVerifier);
  sessionStorage.removeItem(STORAGE_KEYS.oauthNonce);

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
    throw new Error(mapOAuthError(data.error, data.error_description));
  }

  if (data.id_token) {
    verifyIdTokenClaims(data.id_token, expectedNonce);
  }

  storeTokens(data);
  return data;
}

function verifyIdTokenClaims(idToken, expectedNonce) {
  const claims = decodeJwt(idToken);
  if (!claims) {
    throw new Error('Invalid id_token');
  }
  if (expectedNonce == null || expectedNonce === '') {
    throw new Error('Missing OIDC nonce for id_token validation');
  }
  if (claims.nonce !== expectedNonce) {
    throw new Error('id_token nonce mismatch');
  }
  const audience = Array.isArray(claims.aud) ? claims.aud[0] : claims.aud;
  if (audience && audience !== CLIENT_ID) {
    throw new Error('id_token audience mismatch');
  }
  if (isTokenExpired(idToken)) {
    throw new Error('id_token expired');
  }
  return claims;
}

export function storeTokens(data) {
  sessionStorage.setItem(STORAGE_KEYS.accessToken, data.access_token);
  if (data.expires_in != null) {
    const expiresAt = Date.now() + Number(data.expires_in) * 1000;
    sessionStorage.setItem(STORAGE_KEYS.accessTokenExpiresAt, String(expiresAt));
  }
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

function getAccessTokenExpiresAt() {
  const raw = sessionStorage.getItem(STORAGE_KEYS.accessTokenExpiresAt);
  if (raw == null) {
    return null;
  }
  const expiresAt = Number(raw);
  return Number.isFinite(expiresAt) ? expiresAt : null;
}

/** True when the access token should be treated as expired (uses OAuth expires_in, not JWT exp). */
export function isAccessTokenExpired(skewSeconds = 0) {
  const expiresAt = getAccessTokenExpiresAt();
  if (expiresAt != null) {
    return Date.now() >= expiresAt - skewSeconds * 1000;
  }
  const { accessToken } = getStoredTokens();
  return isTokenExpired(accessToken, skewSeconds);
}

export function clearTokens() {
  Object.values(STORAGE_KEYS).forEach((key) => sessionStorage.removeItem(key));
}

function broadcastLogout() {
  localStorage.setItem(LOGOUT_BROADCAST_KEY, String(Date.now()));
}

/** Sync logout across duplicated tabs. Call once at app startup. */
export function initSessionSync() {
  window.addEventListener('storage', (event) => {
    if (event.key !== LOGOUT_BROADCAST_KEY || event.newValue == null) {
      return;
    }
    clearTokens();
    if (window.location.pathname !== '/') {
      window.location.replace('/');
    }
  });
}

/**
 * Verify tokens still exist server-side (e.g. after logout in another tab).
 * Falls back to clearing local session when introspection reports inactive.
 */
export async function validateSession() {
  if (!hasValidSession()) {
    return false;
  }
  try {
    const result = await introspectAccessToken();
    if (!result.active) {
      clearTokens();
      return false;
    }
    return true;
  } catch (err) {
    if (err instanceof SessionExpiredError) {
      clearTokens();
      return false;
    }
    clearTokens();
    return false;
  }
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
  const json = atob(payload.replaceAll('-', '+').replaceAll('_', '/'));
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
  return Boolean(accessToken && !isAccessTokenExpired());
}

export function getMsUntilAccessTokenRefresh() {
  const expiresAt = getAccessTokenExpiresAt();
  if (expiresAt != null) {
    return Math.max(0, expiresAt - REFRESH_SKEW_SECONDS * 1000 - Date.now());
  }
  const { accessToken } = getStoredTokens();
  const claims = decodeJwt(accessToken);
  if (!claims?.exp) {
    return 0;
  }
  return Math.max(0, (claims.exp - REFRESH_SKEW_SECONDS) * 1000 - Date.now());
}

export function redirectToLogin() {
  clearTokens();
  broadcastLogout();
  window.location.href = '/';
}

export async function ensureValidAccessToken() {
  const { accessToken, refreshToken } = getStoredTokens();

  if (accessToken && !isAccessTokenExpired(REFRESH_SKEW_SECONDS)) {
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
    const message = mapOAuthError(data.error, data.error_description);
    if (data.error === 'invalid_grant') {
      throw new SessionExpiredError(message);
    }
    throw new Error(message);
  }

  storeTokens(data);
  return data;
}

export async function introspectAccessToken(token) {
  const accessToken = token ?? (await ensureValidAccessToken());
  if (!accessToken) {
    throw new Error('No access token available');
  }

  const body = new URLSearchParams({
    token: accessToken,
    token_type_hint: 'access_token',
  });

  const response = await fetch('/api/oauth/introspect', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error_description || data.error || 'Introspection failed');
  }
  return data;
}

async function revokeToken(token, tokenTypeHint) {
  const body = new URLSearchParams({ token });
  if (tokenTypeHint) {
    body.set('token_type_hint', tokenTypeHint);
  }

  const response = await fetch('/api/oauth/revoke', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
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
  broadcastLogout();
  const params = new URLSearchParams({ redirect_uri: POST_LOGOUT_REDIRECT_URI });
  window.location.href = `${AUTH_SERVER}/logout?${params.toString()}`;
}
