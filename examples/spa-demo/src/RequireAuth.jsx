import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { validateSession } from './auth';

function RequireAuth({ children }) {
  const [allowed, setAllowed] = useState(null);

  useEffect(() => {
    let cancelled = false;
    validateSession().then((ok) => {
      if (!cancelled) {
        setAllowed(ok);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (allowed === null) {
    return (
      <div className="card">
        <p>Checking session…</p>
      </div>
    );
  }

  if (!allowed) {
    return <Navigate to="/" replace />;
  }

  return children;
}

export default RequireAuth;
