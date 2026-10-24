import type { AuthStatus, GameDto, GameMode, MoveRequest, PlayerColor, UserDto } from '../types';
import { csrfTokenFrom } from '../lib/cookies';

const BASE = '/api';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly title: string,
    public readonly detail: string,
  ) {
    super(`${title}: ${detail}`);
    this.name = 'ApiError';
  }
}

interface ProblemDetail {
  title?: string;
  detail?: string;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((init?.headers as Record<string, string> | undefined) ?? {}),
  };

  // The session cookie authenticates us; the CSRF cookie must be echoed on mutations.
  if (method !== 'GET' && method !== 'HEAD') {
    const token = csrfTokenFrom(document.cookie);
    if (token) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }

  const response = await fetch(`${BASE}${path}`, { credentials: 'same-origin', ...init, headers });

  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as ProblemDetail;
    throw new ApiError(
      response.status,
      problem.title ?? 'Request failed',
      problem.detail ?? response.statusText,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await response.text();
  return (body ? JSON.parse(body) : undefined) as T;
}

export const api = {
  auth: {
    me(): Promise<AuthStatus> {
      return request<AuthStatus>('/auth/me');
    },
    register(body: { email: string; password: string; displayName: string }): Promise<UserDto> {
      return request<UserDto>('/auth/register', { method: 'POST', body: JSON.stringify(body) });
    },
    login(body: { email: string; password: string }): Promise<UserDto> {
      return request<UserDto>('/auth/login', { method: 'POST', body: JSON.stringify(body) });
    },
    logout(): Promise<void> {
      return request<void>('/auth/logout', { method: 'POST' });
    },
  },

  createGame(body: { mode: GameMode; preferredColor: PlayerColor }): Promise<GameDto> {
    return request<GameDto>('/games', { method: 'POST', body: JSON.stringify(body) });
  },

  getGame(id: string): Promise<GameDto> {
    return request<GameDto>(`/games/${id}`);
  },

  listOpenGames(): Promise<GameDto[]> {
    return request<GameDto[]>('/games?status=WAITING_FOR_OPPONENT');
  },

  joinGame(id: string): Promise<GameDto> {
    return request<GameDto>(`/games/${id}/join`, { method: 'POST' });
  },

  move(id: string, move: MoveRequest): Promise<GameDto> {
    return request<GameDto>(`/games/${id}/move`, { method: 'POST', body: JSON.stringify(move) });
  },

  resign(id: string): Promise<GameDto> {
    return request<GameDto>(`/games/${id}/resign`, { method: 'POST' });
  },
};
