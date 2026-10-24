package com.ccmmd.chess.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** One played move, stored in UCI form. Immutable in practice once appended. */
@Embeddable
public class MoveRecord implements Serializable {

    @Column(name = "ply_number", nullable = false)
    private int ply;

    @Column(name = "from_square", length = 2, nullable = false)
    private String from;

    @Column(name = "to_square", length = 2, nullable = false)
    private String to;

    @Column(name = "promotion", length = 1)
    private String promotion;

    @Column(name = "uci", length = 5, nullable = false)
    private String uci;

    @Column(name = "fen_after", length = 120, nullable = false)
    private String fenAfter;

    protected MoveRecord() {
    }

    public MoveRecord(int ply, String from, String to, String promotion, String uci, String fenAfter) {
        this.ply = ply;
        this.from = from;
        this.to = to;
        this.promotion = promotion;
        this.uci = uci;
        this.fenAfter = fenAfter;
    }

    public int getPly() {
        return ply;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getPromotion() {
        return promotion;
    }

    public String getUci() {
        return uci;
    }

    public String getFenAfter() {
        return fenAfter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MoveRecord that)) {
            return false;
        }
        return ply == that.ply && Objects.equals(uci, that.uci) && Objects.equals(fenAfter, that.fenAfter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ply, uci, fenAfter);
    }
}
