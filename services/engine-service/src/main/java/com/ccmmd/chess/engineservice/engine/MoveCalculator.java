package com.ccmmd.chess.engineservice.engine;

/** A source of moves for a position. */
public interface MoveCalculator {

    String name();

    /**
     * @return the chosen move in UCI
     * @throws EngineException when the engine cannot produce a move
     */
    String bestMove(String fen, String difficulty);
}
