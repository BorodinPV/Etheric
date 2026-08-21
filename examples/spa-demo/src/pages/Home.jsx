import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { hasValidSession, startLogin, startRegistration, validateSession } from '../auth';

export default function Home() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [checkingSession, setCheckingSession] = useState(hasValidSession());

  useEffect(() => {
    if (searchParams.get('registered') === '1') {
      window.history.replaceState({}, '', '/');
    }
  }, [searchParams]);

  useEffect(() => {
    if (!hasValidSession()) {
      setCheckingSession(false);
      return;
    }

    let cancelled = false;
    validateSession().then((ok) => {
      if (cancelled) {
        return;
      }
      if (ok) {
        navigate('/dashboard', { replace: true });
      } else {
        setCheckingSession(false);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  if (checkingSession) {
    return (
      <div className="card">
        <p>Checking session…</p>
      </div>
    );
  }

  const registered = searchParams.get('registered') === '1';

  return (
    <div className="card">
      <h1>Etheric SPA Demo</h1>
      <p>
        Public OAuth client using Authorization Code + PKCE (S256) against Etheric at{' '}
        <code>http://localhost:8080</code>.
      </p>
      <p>No <code>client_secret</code> is used in this frontend.</p>
      {registered ? (
        <p className="success">Account created. Sign in to continue.</p>
      ) : null}
      <div className="actions">
        <button type="button" onClick={() => startLogin()}>
          Login with Etheric
        </button>
        <button type="button" className="secondary" onClick={() => startRegistration()}>
          Create account
        </button>
      </div>
    </div>
  );
}
