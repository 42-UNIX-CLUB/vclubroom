package pt.vclubroom.club.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** One period of a club's life: era 1 starts at approval; every archival ends an era, every revival starts one. */
@Embeddable
public class ClubEra {

    @Column(name = "era_number", nullable = false)
    private int number;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_reason", nullable = false)
    private EraStartReason startReason;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_reason")
    private String endReason;

    protected ClubEra() {
        // JPA
    }

    static ClubEra start(int number, EraStartReason reason, Instant at) {
        ClubEra era = new ClubEra();
        era.number = number;
        era.startReason = reason;
        era.startedAt = at;
        return era;
    }

    void end(Instant at, String reason) {
        this.endedAt = at;
        this.endReason = reason;
    }

    public int number() { return number; }
    public Instant startedAt() { return startedAt; }
    public EraStartReason startReason() { return startReason; }
    public Instant endedAt() { return endedAt; }
    public String endReason() { return endReason; }
}