package io.github.juanjguerrer.meslite.event;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MachineStateEventRepository
    extends JpaRepository<MachineStateEvent, Long>,
    JpaSpecificationExecutor<MachineStateEvent> {
    Optional<MachineStateEvent> findByEventId(UUID eventId);
}
