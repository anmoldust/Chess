export type GameStatus =
  | 'WAITING_FOR_OPPONENT'
  | 'IN_PROGRESS'
  | 'WHITE_WON'
  | 'BLACK_WON'
  | 'DRAW';

export type GameMode = 'HUMAN_VS_HUMAN' | 'HUMAN_VS_AI';
export type PlayerColor = 'WHITE' | 'BLACK' | 'RANDOM';

export interface UserDto {
  id: string;
  email: string;
  displayName: string;
}

export interface AuthStatus {
  authenticated: boolean;
  user: UserDto | null;
}

/**
 * What register/login actually return: the account *inside* a wrapper, alongside the token.
 * The client only ever uses `.user` — the token stays server-side in the gateway session.
 */
export interface TokenResponse {
  user: UserDto;
  token: string;
  expiresAt: string;
}

export interface MoveDto {
  ply: number;
  from: string;
  to: string;
  promotion: string | null;
  uci: string;
  fenAfter: string;
}

export interface GameDto {
  id: string;
  fen: string;
  status: GameStatus;
  mode: GameMode;
  whitePlayer: string | null;
  blackPlayer: string | null;
  whiteUserId: string | null;
  blackUserId: string | null;
  aiColor: PlayerColor | null;
  turn: PlayerColor;
  result: string | null;
  moves: MoveDto[];
  version: number;
}

export interface MoveRequest {
  from: string;
  to: string;
  promotion?: string | null;
}

export interface ErrorDto {
  code: string;
  detail: string;
}

/** Where a queued player stands. `position` is 1-based; 0 means "not in the queue". */
export interface QueueStatus {
  queued: boolean;
  position: number;
  queuedAt: string;
}

/** The lobby polls this; `gameId` is only set once a pairing exists. */
export interface MatchmakingStatus {
  queued: boolean;
  matched: boolean;
  gameId: string | null;
}

export interface NotificationDto {
  id: string;
  kind: string;
  message: string;
  createdAt: string;
}

export interface PresenceStatus {
  userId: string;
  online: boolean;
  gameId: string | null;
}

/**
 * `engineBacked` is the honest bit: false means these are material counts from a heuristic,
 * not an engine evaluation. Show it to the user rather than passing it off as accuracy.
 */
export interface AnalysisReport {
  gameId: string;
  accuracyWhite: number;
  accuracyBlack: number;
  blundersWhite: number;
  blundersBlack: number;
  movesAnalysed: number;
  engineBacked: boolean;
  createdAt: string;
}
