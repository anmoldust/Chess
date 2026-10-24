import { describe, expect, it } from 'vitest';
import { isPromotionMove, toUci, turnFromFen } from './chessUtils';

const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

describe('toUci', () => {
  it('builds a plain move', () => {
    expect(toUci('e2', 'e4')).toBe('e2e4');
  });

  it('appends a lowercase promotion piece', () => {
    expect(toUci('e7', 'e8', 'Q')).toBe('e7e8q');
  });
});

describe('isPromotionMove', () => {
  it('detects a white promotion', () => {
    expect(isPromotionMove('a7', 'a8')).toBe(true);
  });

  it('detects a black promotion', () => {
    expect(isPromotionMove('h2', 'h1')).toBe(true);
  });

  it('is false for a normal move', () => {
    expect(isPromotionMove('e2', 'e4')).toBe(false);
  });
});

describe('turnFromFen', () => {
  it('reads white to move', () => {
    expect(turnFromFen(START_FEN)).toBe('WHITE');
  });

  it('reads black to move', () => {
    expect(turnFromFen(START_FEN.replace(' w ', ' b '))).toBe('BLACK');
  });
});
