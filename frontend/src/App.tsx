import { useEffect } from 'react';
import { api } from './api/client';
import { AuthPanel } from './components/AuthPanel';
import { navigate, useHashRoute } from './lib/useHashRoute';
import { GamePage } from './pages/GamePage';
import { LobbyPage } from './pages/LobbyPage';
import { ProfilePage } from './pages/ProfilePage';
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

  // Presence is a heartbeat, not a connection: the server's TTL expires on its own, so there is
  // nothing to announce on an abrupt close. 45s against a 90s TTL leaves room for one dropped beat.
  useEffect(() => {
    if (!user) {
      return;
    }
    const ping = () => {
      void api.presence.heartbeat().catch(() => {
        // Presence is advisory; never let it surface as an error in the UI.
      });
    };
    ping();
    const timer = window.setInterval(ping, 45_000);
    return () => window.clearInterval(timer);
  }, [user]);

  const signOut = async () => {
    try {
      await api.presence.offline().catch(() => undefined);
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
            <button className="link" onClick={() => navigate({ name: 'profile' })}>
              {user.displayName}
            </button>
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
        ) : route.name === 'profile' ? (
          <ProfilePage />
        ) : (
          <LobbyPage />
        )}
      </main>
    </div>
  );
}
