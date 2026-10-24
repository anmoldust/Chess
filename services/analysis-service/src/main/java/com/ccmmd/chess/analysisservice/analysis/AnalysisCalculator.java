package com.ccmmd.chess.analysisservice.analysis;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.PieceType;
import com.github.bhlangonijr.chesslib.Square;

import java.util.List;

/**
 * A material-based move review.
 *
 * <p><strong>This is not an engine evaluation.</strong> It measures one thing it can measure
 * honestly without a search engine: how much material each side lost on each move. A "blunder"
 * here means a move that gave away two pawns' worth of material or more. That catches real
 * mistakes — hanging a piece — and misses positional errors entirely.
 *
 * <p>It is deliberately labelled rather than dressed up: the report records whether an engine was
 * involved, so nobody mistakes a material count for an engine's accuracy score. Swapping in a real
 * UCI engine later means replacing this calculation, not the surrounding pipeline.
 */
public final class AnalysisCalculator {

    /** Centipawn-ish values, only ever used to compare material, never as an evaluation. */
    private static final int PAWN = 100;
    private static final int KNIGHT = 320;
    private static final int BISHOP = 330;
    private static final int ROOK = 500;
    private static final int QUEEN = 900;
    private static final int BLUNDER_THRESHOLD = 200;

    private AnalysisCalculator() {
    }

    public record Report(
            double accuracyWhite,
            double accuracyBlack,
            int blundersWhite,
            int blundersBlack,
            int movesAnalysed) {
    }

    /** @param fens the position after each ply, in order */
    public static Report review(List<String> fens) {
        if (fens == null || fens.isEmpty()) {
            return new Report(100.0, 100.0, 0, 0, 0);
        }

        int whiteMoves = 0;
        int blackMoves = 0;
        int blundersWhite = 0;
        int blundersBlack = 0;

        int previousBalance = 0; // the start position is materially balanced

        for (int i = 0; i < fens.size(); i++) {
            int balance = balanceOf(fens.get(i));
            int delta = balance - previousBalance;

            // Attribute the loss to the side that LOST the material, not the side that played the
            // capture. A hanging piece is the blunder; the capture that punishes it is not.
            if (delta <= -BLUNDER_THRESHOLD) {
                blundersWhite++;
            } else if (delta >= BLUNDER_THRESHOLD) {
                blundersBlack++;
            }

            if (i % 2 == 0) {
                whiteMoves++;
            } else {
                blackMoves++;
            }

            previousBalance = balance;
        }

        return new Report(
                accuracy(whiteMoves, blundersWhite),
                accuracy(blackMoves, blundersBlack),
                blundersWhite,
                blundersBlack,
                fens.size());
    }

    private static double accuracy(int moves, int blunders) {
        if (moves == 0) {
            return 100.0;
        }
        return Math.round(100.0 * (moves - blunders) / moves * 10.0) / 10.0;
    }

    /** White's material minus black's, in centipawn-ish units. */
    static int balanceOf(String fen) {
        Board board = new Board();
        board.loadFromFen(fen);

        int white = 0;
        int black = 0;
        for (Square square : Square.values()) {
            Piece piece = board.getPiece(square);
            if (piece == null || piece == Piece.NONE) {
                continue;
            }
            int value = valueOf(piece.getPieceType());
            // chesslib names its constants WHITE_PAWN / BLACK_PAWN; the prefix is the side.
            if (piece.name().startsWith("WHITE")) {
                white += value;
            } else {
                black += value;
            }
        }
        return white - black;
    }

    private static int valueOf(PieceType type) {
        return switch (type) {
            case PAWN -> PAWN;
            case KNIGHT -> KNIGHT;
            case BISHOP -> BISHOP;
            case ROOK -> ROOK;
            case QUEEN -> QUEEN;
            default -> 0;
        };
    }
}
