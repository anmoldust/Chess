package com.ccmmd.chess.engineservice.engine;

import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.Locale;

/** UCI wire-format helpers, kept pure so the protocol handling is testable without a process. */
public final class UciProtocol {

    private UciProtocol() {
    }

    /**
     * Reads the move out of a {@code bestmove} line.
     *
     * @return the move in UCI (e.g. {@code e2e4}, {@code e7e8q}), or null when the engine reports
     *         {@code (none)} — which means the position has no legal moves, not that it failed
     */
    public static String parseBestMove(String line) {
        if (line == null) {
            return null;
        }
        // Engines are supposed to emit bare lines, but stray whitespace is harmless and real
        // engines have been known to pad output. Be tolerant rather than drop a valid move.
        String trimmed = line.trim();
        if (!trimmed.startsWith("bestmove")) {
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2 || "(none)".equals(parts[1])) {
            return null;
        }
        return parts[1];
    }

    public static String toUci(Move move) {
        StringBuilder sb = new StringBuilder()
                .append(move.getFrom().name().toLowerCase(Locale.ROOT))
                .append(move.getTo().name().toLowerCase(Locale.ROOT));
        Piece promo = move.getPromotion();
        if (promo != null && promo != Piece.NONE) {
            sb.append(switch (promo.getPieceType()) {
                case QUEEN -> "q";
                case ROOK -> "r";
                case BISHOP -> "b";
                case KNIGHT -> "n";
                default -> "";
            });
        }
        return sb.toString();
    }
}
