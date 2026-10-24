export interface EngineRequest {
  fen: string;
  /** Search depth; higher is stronger but slower. */
  depth?: number;
  /** 0-20 UCI skill level, used to weaken the engine for beginners. */
  skill?: number;
  movetimeMs?: number;
}

export interface EngineResult {
  uci: string;
  ponder?: string | null;
}

export interface Engine {
  readonly name: string;
  bestMove(request: EngineRequest): Promise<EngineResult>;
  dispose(): void;
}
