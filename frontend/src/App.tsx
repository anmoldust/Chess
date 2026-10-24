import { useEffect } from 'react';
import { api } from './api/client';
import { AuthPanel } from './components/AuthPanel';
import { navigate, useHashRoute } from './lib/useHashRoute';
import { GamePage } from './pages/GamePage';
import { LobbyPage } from './pages/LobbyPage';
import { useAuthStore } from './store/authStore';

export function App() {
  const route = useHashRoute();
  const user = useAuthStore((s) => s.user);
  const phase = useAuthStore((s) => s.phase);
  const setUser = useAuthStore((s) => s.setUser);
  const setPhase = useAuthStore((s) => s.setPhase);

  // Bootstrap the session once. The GET also plants the CSRF cookie the mutations need.
  useEffect(() => {
    let active = true;
    api.auth
      .me()
      .then((status) => {
        if (active) setUser(status.user);
      })
      .catch(() => {
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setPhase('ready');
      });
    return () => {
      active = false;
    };
  }, [setUser, setPhase]);

  const signOut = async () => {
    try {
      await api.auth.logout();
    } finally {
      setUser(null);
      navigate({ name: 'lobby' });
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <a className="brand" href="#/">
          CCMMD <span>Chess</span>
        </a>
        {user && (
          <div className="header-user">
            <span className="muted">{user.displayName}</span>
            <button className="secondary" onClick={() => void signOut()}>
              Sign out
            </button>
          </div>
        )}
      </header>

      <main className="app-main">
        {phase === 'loading' ? (
          <p className="muted">Loading…</p>
        ) : !user ? (
          <AuthPanel />
        ) : route.name === 'game' ? (
          <GamePage key={route.id} gameId={route.id} />
        ) : (
          <LobbyPage />
        )}
      </main>
    </div>
  );
}
