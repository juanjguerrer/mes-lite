package io.github.juanjguerrer.meslite.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.juanjguerrer.meslite.event.projection.MachineStateRow;
import io.github.juanjguerrer.meslite.event.projection.SequenceGapRow;
import io.github.juanjguerrer.meslite.event.projection.StateDurationRow;

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

    @Query(value = """
        WITH first_event AS (
            SELECT
                id,
                state,
                occurred_at
            FROM machine_state_events
            WHERE machine_id = :machineId
            AND occurred_at < CAST(:from AS timestamptz)
            ORDER BY occurred_at DESC, id DESC
            LIMIT 1
        ),
        events_in_range AS (
            SELECT
                id,
                state,
                occurred_at
            FROM machine_state_events
            WHERE machine_id = :machineId
            AND occurred_at >= CAST(:from AS timestamptz)
            AND occurred_at < CAST(:to AS timestamptz)
        ),
        events_in_range_with_initial AS (
            (SELECT
                id,
                state,
                occurred_at
            FROM first_event)
            UNION ALL
            (SELECT
                id,
                state,
                occurred_at
            FROM events_in_range)
        ),
        events_with_end AS (
        SELECT
            state,
            occurred_at,
            LEAD(occurred_at, 1, CAST(:to AS timestamptz)) OVER (ORDER BY occurred_at, id) AS ended_at
        FROM events_in_range_with_initial
        )

        SELECT
            state,
            CAST(SUM(FLOOR(
                EXTRACT(EPOCH
                    FROM(ended_at - GREATEST(occurred_at, CAST(:from AS timestamptz)))
                )*1000
            )) as BIGINT) as "durationMs"
        FROM events_with_end
        GROUP BY state
        """, nativeQuery = true)
    List<StateDurationRow> sumDurationsByState(
        @Param("machineId") Long machineId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    boolean existsByMachineIdAndSequence(Long machineId, Long sequence);

        @Query(value = """
        WITH sequences AS (
            SELECT sequence FROM machine_state_events
            WHERE machine_id = :machineId AND sequence IS NOT NULL
            UNION ALL
            SELECT sequence FROM production_counts
            WHERE machine_id = :machineId AND sequence IS NOT NULL
        ),
        with_next AS (
            SELECT
                sequence AS from_sequence,
                LEAD(sequence) OVER (ORDER BY sequence) AS to_sequence
            FROM sequences
        )
        SELECT
            from_sequence AS "fromSequence",
            to_sequence AS "toSequence",
            to_sequence - from_sequence - 1 AS "missing"
        FROM with_next
        WHERE to_sequence - from_sequence > 1
        ORDER BY from_sequence
        """, nativeQuery = true)
    List<SequenceGapRow> findSequenceGaps(@Param("machineId") Long machineId);
}
