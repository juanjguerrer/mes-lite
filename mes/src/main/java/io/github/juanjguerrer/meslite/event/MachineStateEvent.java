package io.github.juanjguerrer.meslite.event;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import io.github.juanjguerrer.meslite.machine.Machine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "machine_state_events")
public class MachineStateEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    @Column(nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    private MachineState state;

    private Instant occurredAt;

    @Column(updatable = false)
    @CreationTimestamp
    private Instant receivedAt;

    private String reason;

    public MachineStateEvent(Machine machine, UUID eventId, MachineState state, Instant occurredAt, String reason) {
        this.machine = machine;
        this.eventId = eventId;
        this.state = state;
        this.occurredAt = occurredAt;
        this.reason = reason;
    }

    protected MachineStateEvent() {
        // Default constructor for JPA
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

    public MachineState getState() {
        return state;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getReason() {
        return reason;
    }

    public boolean describesSameFact(Machine machine, MachineState state, Instant occurredAt) {
        return this.machine.getId().equals(machine.getId()) &&
            this.state == state &&
            this.occurredAt.equals(occurredAt);
    }
}
