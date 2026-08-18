import { useNavigate } from 'react-router-dom';
import { getStoredTokens, startLogin } from '../auth';

export default function Home() {
  const navigate = useNavigate();
  const { idToken } = getStoredTokens();

  if (idToken) {
    navigate('/dashboard', { replace: true });
    return null;
  }

  return (
    <div className="card">
      <h1>Etheric SPA Demo</h1>
      <p>
        Public OAuth client using Authorization Code + PKCE (S256) against Etheric at{' '}
        <code>http://localhost:8080</code>.
      </p>
      <p>No <code>client_secret</code> is used in this frontend.</p>
      <button type="button" onClick={() => startLogin()}>
        Login with Etheric
      </button>
    </div>
  );
}
