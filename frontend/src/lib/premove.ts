import { Chess } from 'chess.js';
import { isPromotionMove } from './chessUtils';

export interface ResolvedPremove {
  promotion: string | null;
}

/**
 * Whether a queued premove is legal in the position it has now become, and the promotion to send.
 *
 * <p>A premove is made while the opponent is thinking, so it is only a wish: it is played if the
 * position allows it once it is the mover's turn, and silently dropped if it does not. Promotions
 * auto-queen, which is the usual premove convention.
 */
export function resolvePremove(fen: string, from: string, to: string): ResolvedPremove | null {
  const promotion = isPromotionMove(from, to) ? 'q' : undefined;
  const game = new Chess(fen);
  try {
    const move = game.move({ from, to, promotion });
    return move ? { promotion: promotion ?? null } : null;
  } catch {
    // chess.js throws on an illegal move; a premove that can no longer be played simply evaporates.
    return null;
  }
}
