import type { Engine } from './engine';
import { createRandomEngine } from './randomEngine';
import { StockfishEngine } from './stockfish';

export type Difficulty = 'easy' | 'medium' | 'hard';

const SETTINGS: Record<Difficulty, { depth: number; skill: number }> = {
  easy: { depth: 4, skill: 3 },
  medium: { depth: 10, skill: 12 },
  hard: { depth: 15, skill: 20 },
};

let sharedEngine: Engine | null = null;

function getEngine(): Engine {
  if (!sharedEngine) {
    sharedEngine = new StockfishEngine();
  }
  return sharedEngine;
}

/**
 * Pick the AI's move. Uses Stockfish; if the WASM worker is unavailable it degrades to a
 * random legal move rather than breaking the game.
 */
export async function chooseAiMove(fen: string, difficulty: Difficulty): Promise<string> {
  const { depth, skill } = SETTINGS[difficulty];
  try {
    const result = await getEngine().bestMove({ fen, depth, skill });
    return result.uci;
  } catch {
    const fallback = await createRandomEngine().bestMove({ fen });
    return fallback.uci;
  }
}
