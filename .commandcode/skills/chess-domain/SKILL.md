---
name: chess-domain
description: Chess rules and correctness invariants for this repo — legal move generation, FEN handling, perft verification, and game-state transitions. Use whenever writing or reviewing chess logic, move validation, board state, or engine integration.
---

# Chess domain correctness

This repository treats chess rules as **critical correctness code**. Apply these invariants
to every change that touches board state, move handling, or game status.

## Invariants

1. **King safety is mandatory.** A move that leaves the mover's own king attacked is never
   legal. Generate pseudo-legal moves, then filter by king safety on the *resulting*
   position — including the en-passant discovered-check case.
2. **FEN round-trips losslessly.** `parse(fen).toFen() == fen` for any legal FEN. Never drop
   castling rights, en-passant square, or halfmove/fullmove counters.
3. **Perft is the acceptance test.** From the start position:
   `perft(1)=20, perft(2)=400, perft(3)=8902, perft(4)=197281, perft(5)=4865609`.
   Any change to move generation must keep these exact. Add a Kiwipete
   (`r3k2r/p1ppqpb1/...`) position when touching castling/en passant.
4. **Special moves get explicit coverage:** castling (both sides, both colors, blocked
   squares, through-check), en passant, promotion (all four pieces, with capture), and
   promotion-with-check.
5. **Terminal detection order:** checkmate → stalemate → insufficient material →
   fifty-move rule → threefold repetition. Do not report a draw before checking mate.
6. **Server is authoritative.** Never accept a client-provided FEN as the starting point of
   a move; load the stored FEN and re-derive legal moves.

## Rules library

Use **chesslib** (`com.github.bhlangonijr.chesslib`, JitPack) as the single source of truth:
`Board.loadFromFen`, `Board.legalMoves()`, `Board.doMove`, `Board.isMated`, `Board.isDraw`,
`Board.isStaleMate`, `Board.getSideToMove`. Do not hand-roll move legality.

## Reviewing a change

When reviewing chess logic, ask: does this change alter move generation? If yes, perft must
still pass and a new targeted test must cover the changed special case. If a bug is found,
add the failing position as a regression test before fixing.
