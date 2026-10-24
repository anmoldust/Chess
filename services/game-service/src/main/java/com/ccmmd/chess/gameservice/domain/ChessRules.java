package com.ccmmd.chess.gameservice.domain;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.List;
import java.util.Locale;

/**
 * Thin, stateless wrapper around chesslib. This is the ONLY place that decides what is a
 * legal move, what a terminal position is, and how a board is serialized. Everything else
 * in the service trusts these results.
 */
public final class ChessRules {

    public static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private ChessRules() {
    }

    public record AppliedMove(String uci, String fenAfter, GameStatus status, GameEndReason reason) {
    }

    public static Board board(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);
        return board;
    }

    public static PlayerColor turnOf(String fen) {
        return board(fen).getSideToMove() == Side.WHITE ? PlayerColor.WHITE : PlayerColor.BLACK;
    }

    /**
     * Validate and apply a move against the stored FEN. Returns null when the move is not
     * legal in this position — callers must treat null as an illegal-move rejection.
     *
     * <p>The reason here excludes threefold repetition: that needs the game's history, which this
     * method does not have. The caller adds it.
     */
    public static AppliedMove apply(String fen, String from, String to, String promotion) {
        Board board = board(fen);
        Move move = matchLegalMove(board, from, to, promotion);
        if (move == null) {
            return null;
        }
        board.doMove(move);
        Outcome outcome = evaluate(board, false);
        return new AppliedMove(uci(move), board.getFen(), outcome.status(), outcome.reason());
    }

    private static Move matchLegalMove(Board board, String from, String to, String promotion) {
        Square fromSquare = parseSquare(from);
        Square toSquare = parseSquare(to);
        PieceType promotionType = parsePromotion(promotion);

        for (Move candidate : board.legalMoves()) {
            if (candidate.getFrom() != fromSquare || candidate.getTo() != toSquare) {
                continue;
            }
            Piece promo = candidate.getPromotion();
            boolean candidatePromotes = promo != null && promo != Piece.NONE;
            if (promotionType == null) {
                if (!candidatePromotes) {
                    return candidate;
                }
            } else if (candidatePromotes && promo.getPieceType() == promotionType) {
                return candidate;
            }
        }
        return null;
    }

    public static Square parseSquare(String value) {
        if (value == null || value.length() != 2) {
            throw new IllegalArgumentException("Invalid square: " + value);
        }
        return Square.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public static PieceType parsePromotion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "Q" -> PieceType.QUEEN;
            case "R" -> PieceType.ROOK;
            case "B" -> PieceType.BISHOP;
            case "N" -> PieceType.KNIGHT;
            default -> throw new IllegalArgumentException("Invalid promotion piece: " + value);
        };
    }

    public static String uci(Move move) {
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

    /** The outcome of a position, not just the status — the reason is what a player wants to read. */
    public record Outcome(GameStatus status, GameEndReason reason) {
    }

    /**
     * Terminal-state detection, in priority order: checkmate before any draw.
     *
     * <p>Threefold repetition cannot be seen in a bare FEN — it needs the game's history — so it is
     * passed in by the caller, which owns the move list.
     */
    public static Outcome evaluate(Board board, boolean threefoldRepetition) {
        if (board.isMated()) {
            return new Outcome(
                    board.getSideToMove() == Side.WHITE ? GameStatus.BLACK_WON : GameStatus.WHITE_WON,
                    GameEndReason.CHECKMATE);
        }
        if (board.isStaleMate()) {
            return new Outcome(GameStatus.DRAW, GameEndReason.STALEMATE);
        }
        if (board.isInsufficientMaterial()) {
            return new Outcome(GameStatus.DRAW, GameEndReason.INSUFFICIENT_MATERIAL);
        }
        if (board.getHalfMoveCounter() >= 100) {
            return new Outcome(GameStatus.DRAW, GameEndReason.FIFTY_MOVE_RULE);
        }
        if (threefoldRepetition) {
            return new Outcome(GameStatus.DRAW, GameEndReason.THREEFOLD_REPETITION);
        }
        return new Outcome(GameStatus.IN_PROGRESS, null);
    }

    /** Status-only view, for callers with no history. */
    public static GameStatus evaluateStatus(Board board) {
        return evaluate(board, false).status();
    }

    public static List<Move> legalMoves(String fen) {
        return board(fen).legalMoves();
    }
}
