import { Chess } from 'chess.js';
import type { Engine, EngineRequest, EngineResult } from './engine';

/** Trivial engine used as a fallback if the Stockfish worker cannot be created. */
export function createRandomEngine(): Engine {
  return {
    name: 'random',
    async bestMove(request: EngineRequest): Promise<EngineResult> {
      const game = new Chess(request.fen);
      const moves = game.moves({ verbose: true });
      if (moves.length === 0) {
        throw new Error('No legal moves in position');
      }
      const pick = moves[Math.floor(Math.random() * moves.length)];
      return { uci: `${pick.from}${pick.to}${pick.promotion ?? ''}` };
    },
    dispose() {
      /* nothing to release */
    },
  };
}
