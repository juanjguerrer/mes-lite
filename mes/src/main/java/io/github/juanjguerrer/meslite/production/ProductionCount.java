package io.github.juanjguerrer.meslite.production;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import io.github.juanjguerrer.meslite.machine.Machine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_counts")
public class ProductionCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private long goodCount;

    @Column(nullable = false)
    private long scrapCount;

    @Column(nullable = false)
    private Instant intervalStart;

    @Column(nullable = false)
    private Instant intervalEnd;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(updatable = false)
    private Long sequence;

    protected ProductionCount() {
        // Default constructor for JPA
    }

    public ProductionCount(Machine machine, UUID eventId, long goodCount, long scrapCount, Instant intervalStart, Instant intervalEnd) {
        this.machine = machine;
        this.eventId = eventId;
        this.goodCount = goodCount;
        this.scrapCount = scrapCount;
        this.intervalStart = intervalStart;
        this.intervalEnd = intervalEnd;
    }

    public boolean describesSameFact(Machine machine, long goodCount, long scrapCount, Instant intervalStart, Instant intervalEnd, Long sequence) {
        return this.machine.getId().equals(machine.getId())
            && this.goodCount == goodCount
            && this.scrapCount == scrapCount
            && this.intervalStart.equals(intervalStart)
            && this.intervalEnd.equals(intervalEnd)
            && Objects.equals(this.sequence, sequence);
    }

    public Long getId() {
        return id;
    }

    public Machine getMachine() {
        return machine;
    }

    public UUID getEventId() {
        return eventId;
    }

    public long getGoodCount() {
        return goodCount;
    }

    public long getScrapCount() {
        return scrapCount;
    }

    public Instant getIntervalStart() {
        return intervalStart;
    }

    public Instant getIntervalEnd() {
        return intervalEnd;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Long getSequence() {
        return sequence;
    }
}
