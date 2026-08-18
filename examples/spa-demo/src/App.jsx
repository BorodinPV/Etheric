import { Routes, Route, Navigate } from 'react-router-dom';
import Home from './pages/Home';
import Callback from './pages/Callback';
import Dashboard from './pages/Dashboard';
import { getStoredTokens } from './auth';

function RequireAuth({ children }) {
  const { idToken } = getStoredTokens();
  if (!idToken) {
    return <Navigate to="/" replace />;
  }
  return children;
}

export default function App() {
  return (
    <main>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/callback" element={<Callback />} />
        <Route
          path="/dashboard"
          element={
            <RequireAuth>
              <Dashboard />
            </RequireAuth>
          }
        />
      </Routes>
    </main>
  );
}
