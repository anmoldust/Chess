package com.ccmmd.chess.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single game. The authoritative board state is {@code fen}; {@code moves} are an
 * append-only audit trail. Each seat records the owning account (null for the computer),
 * so authorization is decided from data, never from what a client claims.
 */
@Entity
@Table(name = "games")
public class Game {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private GameMode mode;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private GameStatus status;

    @Column(length = 120, nullable = false)
    private String fen;

    @Column(name = "white_user_id", length = 36)
    private String whiteUserId;

    @Column(name = "white_name", length = 60)
    private String whiteName;

    @Column(name = "black_user_id", length = 36)
    private String blackUserId;

    @Column(name = "black_name", length = 60)
    private String blackName;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_color", length = 8)
    private PlayerColor aiColor;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "game_moves", joinColumns = @JoinColumn(name = "game_id"))
    @OrderColumn(name = "move_index")
    private List<MoveRecord> moves = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Game() {
    }

    public Game(String id, GameMode mode, GameStatus status, String fen) {
        this.id = id;
        this.mode = mode;
        this.status = status;
        this.fen = fen;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void seatWhite(String userId, String name) {
        this.whiteUserId = userId;
        this.whiteName = name;
        touch();
    }

    public void seatBlack(String userId, String name) {
        this.blackUserId = userId;
        this.blackName = name;
        touch();
    }

    public void appendMove(MoveRecord record) {
        this.moves.add(record);
        touch();
    }

    public void setFen(String fen) {
        this.fen = fen;
        touch();
    }

    public void setStatus(GameStatus status) {
        this.status = status;
        touch();
    }

    public void setAiColor(PlayerColor aiColor) {
        this.aiColor = aiColor;
    }

    /** The account id seated on the given color, or null for an empty or computer seat. */
    public String playerUserId(PlayerColor color) {
        return color == PlayerColor.WHITE ? whiteUserId : blackUserId;
    }

    public String playerName(PlayerColor color) {
        return color == PlayerColor.WHITE ? whiteName : blackName;
    }

    /** The color this account is seated on, or null if it is not a player here. */
    public PlayerColor colorOf(String userId) {
        if (userId == null) {
            return null;
        }
        if (userId.equals(whiteUserId)) {
            return PlayerColor.WHITE;
        }
        if (userId.equals(blackUserId)) {
            return PlayerColor.BLACK;
        }
        return null;
    }

    /** In a game against the computer, the human's account id (the non-AI seat). */
    public String humanUserId() {
        if (aiColor == null) {
            return null;
        }
        return playerUserId(aiColor.opposite());
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public GameMode getMode() {
        return mode;
    }

    public GameStatus getStatus() {
        return status;
    }

    public String getFen() {
        return fen;
    }

    public String getWhiteUserId() {
        return whiteUserId;
    }

    public String getWhiteName() {
        return whiteName;
    }

    public String getBlackUserId() {
        return blackUserId;
    }

    public String getBlackName() {
        return blackName;
    }

    public PlayerColor getAiColor() {
        return aiColor;
    }

    public List<MoveRecord> getMoves() {
        return moves;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
