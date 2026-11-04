import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '../store/authStore';
import { AuthPanel } from './AuthPanel';

const authApi = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
}));

vi.mock('../api/client', () => ({ api: { auth: authApi } }));

/** The sign-in tab and the submit control share the name "Sign in"; pick the submit one. */
function submitButton(): HTMLButtonElement {
  const found = screen.getAllByRole('button').find((b) => (b as HTMLButtonElement).type === 'submit');
  if (!found) {
    throw new Error('no submit button rendered');
  }
  return found as HTMLButtonElement;
}

describe('AuthPanel', () => {
  beforeEach(() => {
    authApi.login.mockReset();
    authApi.register.mockReset();
    authApi.forgotPassword.mockReset();
    authApi.resetPassword.mockReset();
    useAuthStore.setState({ user: null, phase: 'ready' });
    window.location.hash = '';
  });

  it('signs in and stores the account', async () => {
    const account = { id: 'u1', email: 'ada@example.com', displayName: 'Ada' };
    authApi.login.mockResolvedValue(account);

    render(<AuthPanel />);
    await userEvent.type(screen.getByLabelText('Email'), 'ada@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'password123');
    await userEvent.click(submitButton());

    expect(authApi.login).toHaveBeenCalledWith({ email: 'ada@example.com', password: 'password123' });
    expect(useAuthStore.getState().user).toEqual(account);
  });

  it('registers from the register tab', async () => {
    authApi.register.mockResolvedValue({ id: 'u2', email: 'new@example.com', displayName: 'New' });

    render(<AuthPanel />);
    await userEvent.click(screen.getByRole('button', { name: 'Register' }));
    await userEvent.type(screen.getByLabelText('Email'), 'new@example.com');
    await userEvent.type(screen.getByLabelText('Display name'), 'New');
    await userEvent.type(screen.getByLabelText('Password'), 'password123');
    await userEvent.click(submitButton());

    expect(authApi.register).toHaveBeenCalledWith({
      email: 'new@example.com',
      password: 'password123',
      displayName: 'New',
    });
  });

  it('surfaces a failed sign-in', async () => {
    authApi.login.mockRejectedValue(new Error('bad credentials'));

    render(<AuthPanel />);
    await userEvent.type(screen.getByLabelText('Email'), 'ada@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong');
    await userEvent.click(submitButton());

    expect(await screen.findByText('bad credentials')).toBeTruthy();
  });

  it('fills the reset form when the backend echoes a token (dev mode)', async () => {
    authApi.forgotPassword.mockResolvedValue({ token: 'tok-123' });

    render(<AuthPanel />);
    await userEvent.click(screen.getByRole('button', { name: 'Forgot password?' }));
    await userEvent.type(screen.getByLabelText('Email'), 'ada@example.com');
    await userEvent.click(submitButton());

    const tokenInput = (await screen.findByLabelText('Reset token')) as HTMLInputElement;
    expect(tokenInput.value).toBe('tok-123');
    expect(screen.getByRole('heading', { name: 'Choose a new password' })).toBeTruthy();
  });

  it('shows a neutral notice when no token is echoed', async () => {
    authApi.forgotPassword.mockResolvedValue({});

    render(<AuthPanel />);
    await userEvent.click(screen.getByRole('button', { name: 'Forgot password?' }));
    await userEvent.type(screen.getByLabelText('Email'), 'ada@example.com');
    await userEvent.click(submitButton());

    expect(await screen.findByText(/a reset link has been sent/i)).toBeTruthy();
  });

  it('opens the reset form straight from a reset link', async () => {
    window.location.hash = '#/reset?token=from-link';

    render(<AuthPanel />);

    const tokenInput = (await screen.findByLabelText('Reset token')) as HTMLInputElement;
    expect(tokenInput.value).toBe('from-link');
  });

  it('resets the password and returns to sign in', async () => {
    window.location.hash = '#/reset?token=from-link';
    authApi.resetPassword.mockResolvedValue(undefined);

    render(<AuthPanel />);
    await userEvent.type(await screen.findByLabelText('New password'), 'newpassword123');
    await userEvent.click(submitButton());

    expect(authApi.resetPassword).toHaveBeenCalledWith({ token: 'from-link', password: 'newpassword123' });
    expect(await screen.findByText(/Password changed/i)).toBeTruthy();
  });
});
