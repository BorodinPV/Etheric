import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  decodeJwt,
  getStoredTokens,
  logout,
  refreshAccessToken,
} from '../auth';

export default function Dashboard() {
  const navigate = useNavigate();
  const [tokens, setTokens] = useState(getStoredTokens());
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  const idClaims = decodeJwt(tokens.idToken);
  const accessClaims = decodeJwt(tokens.accessToken);

  async function onRefresh() {
    setError(null);
    setMessage(null);
    try {
      await refreshAccessToken();
      setTokens(getStoredTokens());
      setMessage('Tokens refreshed.');
    } catch (err) {
      setError(err.message);
    }
  }

  function onLogout() {
    logout();
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

      {message && <p>{message}</p>}
      {error && <p className="error">{error}</p>}

      <div style={{ marginTop: '1.5rem' }}>
        <button type="button" onClick={onRefresh}>
          Refresh token
        </button>
        <button type="button" className="secondary" onClick={onLogout}>
          Logout
        </button>
      </div>
    </div>
  );
}
