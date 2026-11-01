export type GameStatus =
  | 'WAITING_FOR_OPPONENT'
  | 'IN_PROGRESS'
  | 'WHITE_WON'
  | 'BLACK_WON'
  | 'DRAW';

export type GameMode = 'HUMAN_VS_HUMAN' | 'HUMAN_VS_AI';
export type PlayerColor = 'WHITE' | 'BLACK' | 'RANDOM';

export type GameEndReason =
  | 'CHECKMATE'
  | 'RESIGNATION'
  | 'TIMEOUT'
  | 'STALEMATE'
  | 'INSUFFICIENT_MATERIAL'
  | 'FIFTY_MOVE_RULE'
  | 'THREEFOLD_REPETITION';

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
 * The response to a reset request. `token` is non-null only when the backend is running with
 * development-only token exposure on; production sends the link by email instead.
 */
export interface PasswordResetResponse {
  token: string | null;
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
  /** The named time control (BULLET/BLITZ/RAPID/CLASSICAL), or null for an unnamed clock. */
  timeControl: string | null;
  whitePlayer: string | null;
  blackPlayer: string | null;
  whiteUserId: string | null;
  blackUserId: string | null;
  aiColor: PlayerColor | null;
  turn: PlayerColor;
  result: string | null;
  /** Why it ended — what a player actually wants to read. */
  endReason: GameEndReason | null;
  /** The side with an open draw offer. The opponent accepts by offering back. */
  drawOfferBy: PlayerColor | null;
  /** The side with an open rematch offer, or null. Both sides offering starts a new game. */
  rematchOfferBy: PlayerColor | null;
  /** Set once a rematch is agreed: the new game both clients should open. */
  rematchGameId: string | null;
  /** Remaining milliseconds at the moment of this snapshot; the client ticks from there. */
  whiteTimeMs: number | null;
  blackTimeMs: number | null;
  incrementMs: number;
  moves: MoveDto[];
  version: number;
}

/** A game as it appears in a list — a history row, without the move list. */
export interface GameSummaryDto {
  id: string;
  status: GameStatus;
  mode: GameMode;
  whitePlayer: string | null;
  blackPlayer: string | null;
  whiteUserId: string | null;
  blackUserId: string | null;
  result: string | null;
  endReason: GameEndReason | null;
  plyCount: number;
  createdAt: string;
}

/** One rating movement, for the progression list on a profile. */
export interface RatingChangeDto {
  gameId: string;
  oldRating: number;
  newRating: number;
  delta: number;
  playedAt: string;
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

/** A player's competitive standing. The rating is the number matchmaking pairs on. */
export interface RatingDto {
  userId: string;
  displayName: string | null;
  rating: number;
  gamesPlayed: number;
  wins: number;
  losses: number;
  draws: number;
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
