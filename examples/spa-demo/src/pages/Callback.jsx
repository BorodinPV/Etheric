import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { handleCallback } from '../auth';

export default function Callback() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState(null);
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) {
      return;
    }

    const code = searchParams.get('code');
    const oauthError = searchParams.get('error');

    if (!code && !oauthError) {
      navigate('/', { replace: true });
      return;
    }

    handled.current = true;

    handleCallback(searchParams)
      .then(() => navigate('/dashboard', { replace: true }))
      .catch((err) => setError(err.message));
  }, [searchParams, navigate]);

  if (error) {
    return (
      <div className="card">
        <h1>Login failed</h1>
        <p className="error">{error}</p>
        <button type="button" onClick={() => navigate('/', { replace: true })}>
          Back to home
        </button>
      </div>
    );
  }

  return (
    <div className="card">
      <p>Completing login…</p>
    </div>
  );
}
