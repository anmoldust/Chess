package com.ccmmd.chess.gameservice.domain;

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
 * append-only audit trail. Each seat records the owning account id from the verified JWT
 * (null for the computer), so authorization is decided from data, never from what a client claims.
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

    /** Only meaningful for AI games; carried into the engine request. */
    @Column(name = "difficulty", length = 16, nullable = false)
    private String difficulty = "MEDIUM";

    @Column(name = "white_time_ms")
    private Long whiteTimeMs;

    @Column(name = "black_time_ms")
    private Long blackTimeMs;

    @Column(name = "increment_ms", nullable = false)
    private long incrementMs;

    /** The starting budget, kept so a waiting game can start its clock when an opponent arrives. */
    @Column(name = "initial_ms")
    private Long initialMs;

    /** When the side currently to move started thinking; null while no clock is running. */
    @Column(name = "clock_started_at")
    private Instant clockStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 32)
    private GameEndReason endReason;

    /** Which side has an open draw offer, or null. Cleared when accepted, declined or the game ends. */
    @Enumerated(EnumType.STRING)
    @Column(name = "draw_offer_by", length = 8)
    private PlayerColor drawOfferBy;

    /** Which side has asked for a rematch, or null. Both sides asking is what starts one. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rematch_offer_by", length = 8)
    private PlayerColor rematchOfferBy;

    /** Set on the finished game once a rematch is agreed, pointing at the new game. */
    @Column(name = "rematch_game_id", length = 36)
    private String rematchGameId;

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

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * Records the time control at creation. The times are set immediately so a waiting game can
     * show them, but the clock does not run — {@code clockStartedAt} stays null until play begins.
     */
    public void prepareClock(long initialMs, long incrementMs) {
        this.initialMs = initialMs;
        this.incrementMs = incrementMs;
        this.whiteTimeMs = initialMs;
        this.blackTimeMs = initialMs;
        touch();
    }

    /** Starts the clock for the side to move. Requires {@link #prepareClock}. */
    public void startClock(Instant now) {
        if (initialMs == null) {
            return;
        }
        this.whiteTimeMs = initialMs;
        this.blackTimeMs = initialMs;
        this.clockStartedAt = now;
        touch();
    }

    public boolean hasClock() {
        return whiteTimeMs != null && blackTimeMs != null;
    }

    public long remainingMs(PlayerColor color) {
        Long remaining = color == PlayerColor.WHITE ? whiteTimeMs : blackTimeMs;
        return remaining == null ? 0L : remaining;
    }

    /** Sets the mover's remaining time and starts the opponent's clock. */
    public void chargeClock(PlayerColor mover, long remainingMs, Instant now) {
        if (mover == PlayerColor.WHITE) {
            this.whiteTimeMs = Math.max(0L, remainingMs) + incrementMs;
        } else {
            this.blackTimeMs = Math.max(0L, remainingMs) + incrementMs;
        }
        this.clockStartedAt = now;
        touch();
    }

    public void stopClock() {
        this.clockStartedAt = null;
        touch();
    }

    public Instant getClockStartedAt() {
        return clockStartedAt;
    }

    public long getIncrementMs() {
        return incrementMs;
    }

    /** The starting budget, so a rematch can be given the same clock as the game it follows. */
    public long getInitialMs() {
        return initialMs == null ? 0L : initialMs;
    }

    public GameEndReason getEndReason() {
        return endReason;
    }

    public void setEndReason(GameEndReason endReason) {
        this.endReason = endReason;
    }

    public PlayerColor getDrawOfferBy() {
        return drawOfferBy;
    }

    public void offerDraw(PlayerColor color) {
        this.drawOfferBy = color;
        touch();
    }

    public void clearDrawOffer() {
        this.drawOfferBy = null;
        touch();
    }

    public PlayerColor getRematchOfferBy() {
        return rematchOfferBy;
    }

    public void offerRematch(PlayerColor color) {
        this.rematchOfferBy = color;
        touch();
    }

    public void clearRematchOffer() {
        this.rematchOfferBy = null;
        touch();
    }

    public String getRematchGameId() {
        return rematchGameId;
    }

    public void setRematchGameId(String rematchGameId) {
        this.rematchGameId = rematchGameId;
        touch();
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
