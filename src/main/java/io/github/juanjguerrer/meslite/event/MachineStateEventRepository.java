package io.github.juanjguerrer.meslite.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MachineStateEventRepository
    extends JpaRepository<MachineStateEvent, Long>,
    JpaSpecificationExecutor<MachineStateEvent> {
    Optional<MachineStateEvent> findByEventId(UUID eventId);
    Optional<MachineStateEvent> findFirstByMachineIdOrderByOccurredAtDescIdDesc(Long machineId);

    @Query(value = """
        SELECT DISTINCT ON (m.id)
        m.id AS "machineId", m.code AS "machineCode", e.state AS "state", e.occurred_at AS "since"
        FROM machines m
        LEFT JOIN machine_state_events e ON e.machine_id = m.id
        WHERE m.line_id = :lineId
        ORDER BY m.id, e.occurred_at DESC, e.id DESC
    """, nativeQuery = true)
    List<MachineStateRow> findLatestByLineId(@Param("lineId") Long lineId);

    Optional<MachineStateEvent> findFirstByMachineIdAndOccurredAtBeforeOrderByOccurredAtDescIdDesc(Long machineId, Instant from);
}
