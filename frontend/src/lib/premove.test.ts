import { describe, expect, it } from 'vitest';
import { resolvePremove } from './premove';

const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
const BLACK_TO_MOVE = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1';
const WHITE_TO_PROMOTE = '4k3/P7/8/8/8/8/8/4K3 w - - 0 1';

describe('resolvePremove', () => {
  it('plays a premove that is legal in the new position', () => {
    expect(resolvePremove(START, 'e2', 'e4')).toEqual({ promotion: null });
  });

  it('drops a premove that is not legal in the new position', () => {
    expect(resolvePremove(START, 'e2', 'e5')).toBeNull();
  });

  it('drops a premove that moves from an empty square', () => {
    expect(resolvePremove(START, 'e3', 'e4')).toBeNull();
  });

  it('drops a premove when it is not that side to move', () => {
    // e2e4 is white's move, but black is to move here.
    expect(resolvePremove(BLACK_TO_MOVE, 'e2', 'e4')).toBeNull();
  });

  it('auto-queens a promotion premove', () => {
    expect(resolvePremove(WHITE_TO_PROMOTE, 'a7', 'a8')).toEqual({ promotion: 'q' });
  });
});
