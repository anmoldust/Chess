import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import { errorMessage } from '../lib/errors';
import { useAuthStore } from '../store/authStore';

type Mode = 'login' | 'register';

export function AuthPanel() {
  const setUser = useAuthStore((s) => s.setUser);

  const [mode, setMode] = useState<Mode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const user =
        mode === 'login'
          ? await api.auth.login({ email, password })
          : await api.auth.register({ email, password, displayName });
      setUser(user);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const switchMode = (next: Mode) => {
    setMode(next);
    setError(null);
  };

  return (
    <div className="auth-wrap">
      <form className="panel auth-panel" onSubmit={submit}>
        <h2>{mode === 'login' ? 'Sign in' : 'Create an account'}</h2>

        <div className="auth-tabs">
          <button
            type="button"
            className={mode === 'login' ? 'tab active' : 'tab'}
            onClick={() => switchMode('login')}
          >
            Sign in
          </button>
          <button
            type="button"
            className={mode === 'register' ? 'tab active' : 'tab'}
            onClick={() => switchMode('register')}
          >
            Register
          </button>
        </div>

        <label className="field">
          Email
          <input
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>

        {mode === 'register' && (
          <label className="field">
            Display name
            <input
              required
              maxLength={60}
              autoComplete="nickname"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          </label>
        )}

        <label className="field">
          Password
          <input
            type="password"
            required
            minLength={mode === 'register' ? 8 : undefined}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>

        <button type="submit" disabled={busy} style={{ marginTop: 16, width: '100%' }}>
          {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
        </button>

        {error && <p className="error">{error}</p>}
        {mode === 'register' && <p className="muted small">Password must be at least 8 characters.</p>}
      </form>
    </div>
  );
}
