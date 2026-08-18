import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  decodeJwt,
  ensureValidAccessToken,
  getMsUntilAccessTokenRefresh,
  getStoredTokens,
  introspectAccessToken,
  logout,
  redirectToLogin,
  refreshAccessToken,
  SessionExpiredError,
} from '../auth';

export default function Dashboard() {
  const navigate = useNavigate();
  const refreshTimerRef = useRef(null);
  const [tokens, setTokens] = useState(getStoredTokens());
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const [introspection, setIntrospection] = useState(null);
  const [introspecting, setIntrospecting] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const idClaims = decodeJwt(tokens.idToken);
  const accessClaims = decodeJwt(tokens.accessToken);

  const runIntrospection = useCallback(async (silent = false) => {
    if (!silent) {
      setIntrospecting(true);
    }
    setError(null);
    try {
      const result = await introspectAccessToken();
      setIntrospection(result);
    } catch (err) {
      if (err instanceof SessionExpiredError) {
        redirectToLogin();
        return;
      }
      setError(err.message);
      setIntrospection(null);
    } finally {
      if (!silent) {
        setIntrospecting(false);
      }
    }
  }, []);

  const scheduleAutoRefresh = useCallback(() => {
    if (refreshTimerRef.current) {
      clearTimeout(refreshTimerRef.current);
    }

    const delay = getMsUntilAccessTokenRefresh();
    refreshTimerRef.current = setTimeout(async () => {
      try {
        await ensureValidAccessToken();
        setTokens(getStoredTokens());
        setMessage('Session refreshed automatically.');
        await runIntrospection(true);
        scheduleAutoRefresh();
      } catch (err) {
        if (err instanceof SessionExpiredError) {
          redirectToLogin();
        } else {
          setError(err.message);
        }
      }
    }, delay);
  }, [runIntrospection]);

  useEffect(() => {
    let cancelled = false;

    async function init() {
      try {
        await ensureValidAccessToken();
        if (cancelled) {
          return;
        }
        setTokens(getStoredTokens());
        await runIntrospection();
        if (!cancelled) {
          scheduleAutoRefresh();
        }
      } catch (err) {
        if (cancelled) {
          return;
        }
        if (err instanceof SessionExpiredError) {
          redirectToLogin();
        } else {
          setError(err.message);
        }
      }
    }

    init();

    return () => {
      cancelled = true;
      if (refreshTimerRef.current) {
        clearTimeout(refreshTimerRef.current);
      }
    };
  }, [runIntrospection, scheduleAutoRefresh]);

  async function onRefresh() {
    setError(null);
    setMessage(null);
    try {
      await refreshAccessToken();
      setTokens(getStoredTokens());
      setMessage('Tokens refreshed.');
      await runIntrospection();
      scheduleAutoRefresh();
    } catch (err) {
      if (err instanceof SessionExpiredError) {
        redirectToLogin();
        return;
      }
      setError(err.message);
    }
  }

  async function onLogout() {
    setLoggingOut(true);
    setError(null);
    if (refreshTimerRef.current) {
      clearTimeout(refreshTimerRef.current);
    }
    try {
      await logout();
    } catch (err) {
      setError(err.message);
      setLoggingOut(false);
    }
  }

  if (!idClaims) {
    navigate('/', { replace: true });
    return null;
  }

  const roles = accessClaims?.groups || [];

  return (
    <div className="card">
      <h1>Dashboard</h1>
      <p>Signed in via Authorization Code + PKCE (public client).</p>

      <dl>
        <dt>sub</dt>
        <dd>{idClaims.sub}</dd>
        <dt>preferred_username</dt>
        <dd>{idClaims.preferred_username || '—'}</dd>
        <dt>email</dt>
        <dd>{idClaims.email || '—'}</dd>
        <dt>roles</dt>
        <dd>{Array.isArray(roles) ? roles.join(', ') : '—'}</dd>
        <dt>scope</dt>
        <dd>{tokens.scope || '—'}</dd>
      </dl>

      <section className="introspection">
        <h2>Token introspection</h2>
        <p>Access token checked via dev-only Vite proxy (confidential client server-side).</p>

        {introspecting && !introspection && <p>Introspecting…</p>}

        {introspection && (
          <>
            <dl>
              <dt>active</dt>
              <dd>{String(introspection.active)}</dd>
              <dt>sub</dt>
              <dd>{introspection.sub || '—'}</dd>
              <dt>scope</dt>
              <dd>{introspection.scope || '—'}</dd>
              <dt>client_id</dt>
              <dd>{introspection.client_id || '—'}</dd>
              <dt>exp</dt>
              <dd>{introspection.exp ?? '—'}</dd>
            </dl>
            <pre>{JSON.stringify(introspection, null, 2)}</pre>
          </>
        )}

        <button type="button" onClick={() => runIntrospection()} disabled={introspecting}>
          {introspecting ? 'Introspecting…' : 'Introspect again'}
        </button>
      </section>

      {message && <p>{message}</p>}
      {error && <p className="error">{error}</p>}

      <div style={{ marginTop: '1.5rem' }}>
        <button type="button" onClick={onRefresh} disabled={loggingOut}>
          Refresh token
        </button>
        <button
          type="button"
          className="secondary"
          onClick={onLogout}
          disabled={loggingOut}
        >
          {loggingOut ? 'Logging out…' : 'Logout'}
        </button>
      </div>
    </div>
  );
}
