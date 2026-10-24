package com.ccmmd.chess.engineservice.engine;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deliberately weak stand-in, used when no UCI engine binary is configured.
 *
 * <p>It exists so the platform is usable out of the box — the bus wiring, authorization and
 * game flow can all be exercised without installing Stockfish. It plays a random legal move, so
 * it is not a substitute for a real engine and says so in its name.
 */
public class RandomLegalEngine implements MoveCalculator {

    @Override
    public String name() {
        return "random-legal-fallback (no UCI engine configured)";
    }

    @Override
    public String bestMove(String fen, String difficulty) {
        Board board = new Board();
        board.loadFromFen(fen);

        List<Move> legal = board.legalMoves();
        if (legal.isEmpty()) {
            throw new EngineException("No legal moves in this position");
        }
        return UciProtocol.toUci(legal.get(ThreadLocalRandom.current().nextInt(legal.size())));
    }
}
