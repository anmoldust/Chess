import type {
  AnalysisReport,
  AuthStatus,
  GameDto,
  GameMode,
  GameSummaryDto,
  MatchmakingStatus,
  MoveRequest,
  NotificationDto,
  PasswordResetResponse,
  PlayerColor,
  PresenceStatus,
  QueueStatus,
  RatingChangeDto,
  RatingDto,
  TokenResponse,
  UserDto,
} from '../types';
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

    // These return {user, token, expiresAt}; the caller wants the account, so unwrap it here
    // rather than leaving every caller to remember the extra layer.
    register(body: { email: string; password: string; displayName: string }): Promise<UserDto> {
      return request<TokenResponse>('/auth/register', {
        method: 'POST',
        body: JSON.stringify(body),
      }).then((response) => response.user);
    },

    login(body: { email: string; password: string }): Promise<UserDto> {
      return request<TokenResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify(body),
      }).then((response) => response.user);
    },

    logout(): Promise<void> {
      return request<void>('/auth/logout', { method: 'POST' });
    },

    /** Always resolves the same way for a known or unknown address; the token is dev-only. */
    forgotPassword(email: string): Promise<PasswordResetResponse> {
      return request<PasswordResetResponse>('/auth/password/forgot', {
        method: 'POST',
        body: JSON.stringify({ email }),
      });
    },

    resetPassword(body: { token: string; password: string }): Promise<void> {
      return request<void>('/auth/password/reset', { method: 'POST', body: JSON.stringify(body) });
    },
  },

  createGame(body: { mode: GameMode; preferredColor: PlayerColor; timeControl: string }): Promise<GameDto> {
    return request<GameDto>('/games', { method: 'POST', body: JSON.stringify(body) });
  },

  getGame(id: string): Promise<GameDto> {
    return request<GameDto>(`/games/${id}`);
  },

  listOpenGames(): Promise<GameDto[]> {
    return request<GameDto[]>('/games?status=WAITING_FOR_OPPONENT');
  },

  /** A player's finished games, newest first. Public, like every game read. */
  listGameHistory(userId: string, limit = 20): Promise<GameSummaryDto[]> {
    return request<GameSummaryDto[]>(
      `/games/history?userId=${encodeURIComponent(userId)}&limit=${limit}`,
    );
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

  /** Offers a draw, or accepts the opponent's open offer. */
  offerDraw(id: string): Promise<GameDto> {
    return request<GameDto>(`/games/${id}/draw`, { method: 'POST' });
  },

  declineDraw(id: string): Promise<GameDto> {
    return request<GameDto>(`/games/${id}/draw/decline`, { method: 'POST' });
  },

  /** Offers a rematch, or accepts the opponent's open offer. */
  requestRematch(id: string): Promise<GameDto> {
    return request<GameDto>(`/games/${id}/rematch`, { method: 'POST' });
  },

  matchmaking: {
    queue(timeControl: string): Promise<QueueStatus> {
      return request<QueueStatus>('/matchmaking/queue', {
        method: 'POST',
        body: JSON.stringify({ timeControl }),
      });
    },

    leave(): Promise<void> {
      return request<void>('/matchmaking/queue', { method: 'DELETE' });
    },

    status(): Promise<MatchmakingStatus> {
      return request<MatchmakingStatus>('/matchmaking/status');
    },
  },

  presence: {
    /** TTL-backed: stop calling it and the player goes offline on their own. */
    heartbeat(): Promise<PresenceStatus> {
      return request<PresenceStatus>('/presence/heartbeat', { method: 'POST' });
    },

    offline(): Promise<void> {
      return request<void>('/presence/offline', { method: 'POST' });
    },

    status(userId: string): Promise<PresenceStatus> {
      return request<PresenceStatus>(`/presence/${userId}`);
    },
  },

  notifications: {
    list(): Promise<NotificationDto[]> {
      return request<NotificationDto[]>('/notifications');
    },
  },

  analysis: {
    forGame(gameId: string): Promise<AnalysisReport> {
      return request<AnalysisReport>(`/analysis/${gameId}`);
    },
  },

  ratings: {
    me(): Promise<RatingDto> {
      return request<RatingDto>('/ratings/me');
    },

    forUser(userId: string): Promise<RatingDto> {
      return request<RatingDto>(`/ratings/${userId}`);
    },

    leaderboard(limit = 10): Promise<RatingDto[]> {
      return request<RatingDto[]>(`/ratings/leaderboard?limit=${limit}`);
    },

    /** A player's rating movements, newest first. */
    history(userId: string, limit = 20): Promise<RatingChangeDto[]> {
      return request<RatingChangeDto[]>(`/ratings/${userId}/history?limit=${limit}`);
    },
  },
};
