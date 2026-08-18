import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  decodeJwt,
  getStoredTokens,
  introspectAccessToken,
  logout,
  refreshAccessToken,
} from '../auth';

export default function Dashboard() {
  const navigate = useNavigate();
  const [tokens, setTokens] = useState(getStoredTokens());
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const [introspection, setIntrospection] = useState(null);
  const [introspecting, setIntrospecting] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  const idClaims = decodeJwt(tokens.idToken);
  const accessClaims = decodeJwt(tokens.accessToken);

  async function runIntrospection() {
    setIntrospecting(true);
    setError(null);
    try {
      const result = await introspectAccessToken();
      setIntrospection(result);
    } catch (err) {
      setError(err.message);
      setIntrospection(null);
    } finally {
      setIntrospecting(false);
    }
  }

  useEffect(() => {
    runIntrospection();
  }, []);

  async function onRefresh() {
    setError(null);
    setMessage(null);
    try {
      await refreshAccessToken();
      setTokens(getStoredTokens());
      setMessage('Tokens refreshed.');
      await runIntrospection();
    } catch (err) {
      setError(err.message);
    }
  }

  async function onLogout() {
    setLoggingOut(true);
    setError(null);
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

        <button type="button" onClick={runIntrospection} disabled={introspecting}>
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
