/** Build a UCI move string, the only move format the server accepts. */
export function toUci(from: string, to: string, promotion?: string | null): string {
  const suffix = promotion ? promotion.toLowerCase() : '';
  return `${from.toLowerCase()}${to.toLowerCase()}${suffix}`;
}

/** True when a move from `from` to `to` lands on the far rank (i.e. needs a promotion piece). */
export function isPromotionMove(from: string, to: string): boolean {
  const fromRank = from[1];
  const toRank = to[1];
  return (fromRank === '7' && toRank === '8') || (fromRank === '2' && toRank === '1');
}

/** Side to move, parsed from a FEN string. */
export function turnFromFen(fen: string): 'WHITE' | 'BLACK' {
  const active = fen.split(' ')[1];
  return active === 'b' ? 'BLACK' : 'WHITE';
}
