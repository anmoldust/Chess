import { useEffect, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import { errorMessage } from '../lib/errors';
import { useAuthStore } from '../store/authStore';

type Mode = 'login' | 'register' | 'forgot' | 'reset';

const HEADING: Record<Mode, string> = {
  login: 'Sign in',
  register: 'Create an account',
  forgot: 'Reset your password',
  reset: 'Choose a new password',
};

/** The emailed link is `#/reset?token=...`; the SPA is not on a route for it, so read the hash. */
function tokenFromHash(): string | null {
  const match = window.location.hash.match(/[?&]token=([^&]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

export function AuthPanel() {
  const setUser = useAuthStore((s) => s.setUser);

  const [mode, setMode] = useState<Mode>(() => (tokenFromHash() ? 'reset' : 'login'));
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [token, setToken] = useState(() => tokenFromHash() ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // A reset link opens the form with the token already filled in.
  useEffect(() => {
    const hashToken = tokenFromHash();
    if (hashToken) {
      setToken(hashToken);
      setMode('reset');
    }
  }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      if (mode === 'login') {
        setUser(await api.auth.login({ email, password }));
      } else if (mode === 'register') {
        setUser(await api.auth.register({ email, password, displayName }));
      } else if (mode === 'forgot') {
        const response = await api.auth.forgotPassword(email);
        if (response.token) {
          // Development only: the backend echoed the token instead of emailing it.
          setToken(response.token);
          setMode('reset');
          setNotice('Your reset token has been filled in. Choose a new password.');
        } else {
          setNotice('If an account exists for that email, a reset link has been sent.');
        }
      } else {
        await api.auth.resetPassword({ token, password });
        setPassword('');
        setMode('login');
        setNotice('Password changed. Sign in with your new password.');
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const switchMode = (next: Mode) => {
    setMode(next);
    setError(null);
    setNotice(null);
  };

  const submitLabel = busy
    ? 'Please wait…'
    : { login: 'Sign in', register: 'Create account', forgot: 'Send reset link', reset: 'Set password' }[mode];

  return (
    <div className="auth-wrap">
      <form className="panel auth-panel" onSubmit={submit}>
        <h2>{HEADING[mode]}</h2>

        {(mode === 'login' || mode === 'register') && (
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
        )}

        {mode === 'forgot' && (
          <p className="muted small">Enter your email and we will send a reset link.</p>
        )}

        {mode !== 'reset' && (
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
        )}

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

        {mode === 'reset' && (
          <label className="field">
            Reset token
            <input
              required
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="paste the token from your reset link"
            />
          </label>
        )}

        {(mode === 'login' || mode === 'register' || mode === 'reset') && (
          <label className="field">
            {mode === 'reset' ? 'New password' : 'Password'}
            <input
              type="password"
              required
              minLength={mode === 'reset' || mode === 'register' ? 8 : undefined}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
        )}

        <button type="submit" disabled={busy} style={{ marginTop: 16, width: '100%' }}>
          {submitLabel}
        </button>

        {notice && <p className="muted small">{notice}</p>}
        {error && <p className="error">{error}</p>}

        {mode === 'login' && (
          <button type="button" className="link" onClick={() => switchMode('forgot')}>
            Forgot password?
          </button>
        )}
        {(mode === 'forgot' || mode === 'reset') && (
          <button type="button" className="link" onClick={() => switchMode('login')}>
            Back to sign in
          </button>
        )}
        {mode === 'register' && <p className="muted small">Password must be at least 8 characters.</p>}
      </form>
    </div>
  );
}
