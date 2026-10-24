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
