package io.github.juanjguerrer.meslite.event;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

public final class MachineStateEventSpecifications {
    private MachineStateEventSpecifications() { }

    public static Specification<MachineStateEvent> machineEquals(Long machineId) {
        return (root, query, cb) -> cb.equal(root.get("machine").get("id"), machineId);
    }

    public static Specification<MachineStateEvent> occurredAtFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
    }

    public static Specification<MachineStateEvent> occurredAtBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get("occurredAt"), to);
    }
}
