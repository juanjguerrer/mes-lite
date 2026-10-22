package io.github.juanjguerrer.meslite.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.event.dto.StateDurationsResponse;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;

/**
 * The Java and the SQL implementations of state durations must agree on every scenario.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StateDurationsParityTest {

    private static final Instant T0600 = Instant.parse("2026-10-19T06:00:00Z");
    private static final Instant T0730 = Instant.parse("2026-10-19T07:30:00Z");
    private static final Instant T0800 = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant T0900 = Instant.parse("2026-10-19T09:00:00Z");
    private static final Instant T1000 = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant T1030 = Instant.parse("2026-10-19T10:30:00Z");
    private static final Instant T1100 = Instant.parse("2026-10-19T11:00:00Z");
    private static final Instant T1200 = Instant.parse("2026-10-19T12:00:00Z");

    @Autowired
    private MachineStateEventService service;

    @Autowired
    private ProductionLineRepository lineRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private MachineStateEventRepository eventRepository;

    private Machine press;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        ProductionLine line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        press = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line));
    }

    // Other integration test classes delete machines; leftover events would block them (FK RESTRICT).
    @AfterEach
    void cleanDatabase() {
        eventRepository.deleteAll();
        machineRepository.deleteAll();
        lineRepository.deleteAll();
    }

    @Test
    void guideExampleUsesTheLatestEventBeforeTheRange() {
        // The 06:00 RUNNING must be ignored: the state at 08:00 comes from 07:30 DOWN.
        save(MachineState.RUNNING, T0600);
        save(MachineState.DOWN, T0730);
        save(MachineState.RUNNING, T0900);
        save(MachineState.IDLE, T1030);
        save(MachineState.RUNNING, T1100);

        StateDurationsResponse result = assertParity(T0800, T1200);

        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(minutes(150));
        assertThat(result.durationsMs().get(MachineState.DOWN)).isEqualTo(minutes(60));
        assertThat(result.durationsMs().get(MachineState.IDLE)).isEqualTo(minutes(30));
        assertThat(result.unknownMs()).isZero();
    }

    @Test
    void machineWithoutEventsIsAllUnknown() {
        StateDurationsResponse result = assertParity(T0800, T1200);

        assertThat(result.unknownMs()).isEqualTo(minutes(240));
    }

    @Test
    void timeBeforeTheFirstEventEverIsUnknown() {
        save(MachineState.RUNNING, T1000);

        StateDurationsResponse result = assertParity(T0800, T1200);

        assertThat(result.unknownMs()).isEqualTo(minutes(120));
        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(minutes(120));
    }

    @Test
    void eventExactlyAtFromStartsTheRange() {
        save(MachineState.DOWN, T0730);
        save(MachineState.SETUP, T0800);

        StateDurationsResponse result = assertParity(T0800, T1200);

        assertThat(result.durationsMs().get(MachineState.SETUP)).isEqualTo(minutes(240));
        assertThat(result.durationsMs().get(MachineState.DOWN)).isZero();
    }

    @Test
    void eventExactlyAtToIsExcluded() {
        save(MachineState.RUNNING, T0800);
        save(MachineState.DOWN, T1200);

        StateDurationsResponse result = assertParity(T0800, T1200);

        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(minutes(240));
    }

    @Test
    void eventsAtTheSameInstantAreOrderedById() {
        save(MachineState.RUNNING, T0800);
        save(MachineState.DOWN, T0900);
        save(MachineState.IDLE, T0900);

        StateDurationsResponse result = assertParity(T0800, T1200);

        assertThat(result.durationsMs().get(MachineState.DOWN)).isZero();
        assertThat(result.durationsMs().get(MachineState.IDLE)).isEqualTo(minutes(180));
    }

    @Test
    void consecutiveEventsWithTheSameStateAddUp() {
        save(MachineState.RUNNING, T0800);
        save(MachineState.RUNNING, T0900);
        save(MachineState.RUNNING, T1100);

        StateDurationsResponse result = assertParity(T0800, T1200);

        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(minutes(240));
    }

    @Test
    void subMillisecondSegmentsAreTruncatedOneByOne() {
        // Three RUNNING segments of 1.6 ms each: per-segment truncation gives 3 ms, summing first would give 4.
        Instant start = T0800;
        save(MachineState.RUNNING, start);
        save(MachineState.RUNNING, start.plusNanos(1_600_000));
        save(MachineState.RUNNING, start.plusNanos(3_200_000));
        save(MachineState.IDLE, start.plusNanos(4_800_000));

        StateDurationsResponse result = assertParity(start, start.plusMillis(10));

        assertThat(result.durationsMs().get(MachineState.RUNNING)).isEqualTo(3);
    }

    private StateDurationsResponse assertParity(Instant from, Instant to) {
        StateDurationsResponse java = service.stateDurations(press.getId(), from, to);
        StateDurationsResponse sql = service.stateDurationsWithSQL(press.getId(), from, to);

        assertThat(sql.durationsMs()).as("durations per state").isEqualTo(java.durationsMs());
        assertThat(sql.unknownMs()).as("unknown time").isEqualTo(java.unknownMs());
        assertThat(sql.from()).isEqualTo(java.from());
        assertThat(sql.to()).isEqualTo(java.to());

        long total = java.durationsMs().values().stream().mapToLong(Long::longValue).sum() + java.unknownMs();
        assertThat(total).as("durations add up to the range").isEqualTo(Duration.between(java.from(), java.to()).toMillis());
        return java;
    }

    private void save(MachineState state, Instant occurredAt) {
        eventRepository.save(new MachineStateEvent(press, UUID.randomUUID(), state, occurredAt, null));
    }

    private static long minutes(long minutes) {
        return Duration.ofMinutes(minutes).toMillis();
    }
}
