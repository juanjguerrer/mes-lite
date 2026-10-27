package io.github.juanjguerrer.meslite.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.common.exception.ConflictException;
import io.github.juanjguerrer.meslite.event.MachineState;
import io.github.juanjguerrer.meslite.event.MachineStateEventRepository;
import io.github.juanjguerrer.meslite.event.MachineStateEventService;
import io.github.juanjguerrer.meslite.event.dto.CreateStateEventRequest;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.production.ProductionCountRepository;
import io.github.juanjguerrer.meslite.production.ProductionCountService;
import io.github.juanjguerrer.meslite.production.dto.CreateProductionCountRequest;

// The services check existsByMachineIdAndSequence before saving, so the unique constraint on
// (machine_id, sequence) is only reached when two ingests race. The first two tests force that
// path by making the pre-check miss; the last two run a real race.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SequenceRaceIntegrationTest {

    private static final String LINE = "LINE-01";
    private static final String MACHINE = "PRS-003";

    @Autowired
    private MachineStateEventService stateService;

    @Autowired
    private ProductionCountService productionService;

    @MockitoSpyBean
    private MachineStateEventRepository eventRepository;

    @MockitoSpyBean
    private ProductionCountRepository countRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private ProductionLineRepository lineRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        var line = lineRepository.save(new ProductionLine("Assembly 1", LINE, null));
        machineRepository.save(new io.github.juanjguerrer.meslite.machine.Machine("Press 3", MACHINE, null, 12000, line));
    }

    // Other integration test classes delete machines; leftover rows would block them (FK RESTRICT).
    @AfterEach
    void cleanDatabase() {
        countRepository.deleteAll();
        eventRepository.deleteAll();
        machineRepository.deleteAll();
        lineRepository.deleteAll();
    }

    @Test
    void stateEventSequenceViolationAfterMissedPreCheckBecomesConflict() {
        stateService.ingest(LINE, MACHINE, stateRequest(UUID.randomUUID(), 1));
        doReturn(false).when(eventRepository).existsByMachineIdAndSequence(anyLong(), anyLong());

        assertThatThrownBy(() -> stateService.ingest(LINE, MACHINE, stateRequest(UUID.randomUUID(), 1)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Sequence 1 was already used");

        verify(eventRepository, times(2)).existsByMachineIdAndSequence(anyLong(), anyLong());
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void productionCountSequenceViolationAfterMissedPreCheckBecomesConflict() {
        productionService.ingest(LINE, MACHINE, countRequest(UUID.randomUUID(), 1));
        doReturn(false).when(countRepository).existsByMachineIdAndSequence(anyLong(), anyLong());

        assertThatThrownBy(() -> productionService.ingest(LINE, MACHINE, countRequest(UUID.randomUUID(), 1)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Sequence 1 was already used");

        verify(countRepository, times(2)).existsByMachineIdAndSequence(anyLong(), anyLong());
        assertThat(countRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentStateEventsWithSameSequenceStoreOnlyOne() throws Exception {
        var results = race(
            () -> stateService.ingest(LINE, MACHINE, stateRequest(UUID.randomUUID(), 1)),
            () -> stateService.ingest(LINE, MACHINE, stateRequest(UUID.randomUUID(), 1))
        );

        assertExactlyOneStoredAndOneConflict(results);
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentProductionCountsWithSameSequenceStoreOnlyOne() throws Exception {
        var results = race(
            () -> productionService.ingest(LINE, MACHINE, countRequest(UUID.randomUUID(), 1)),
            () -> productionService.ingest(LINE, MACHINE, countRequest(UUID.randomUUID(), 1))
        );

        assertExactlyOneStoredAndOneConflict(results);
        assertThat(countRepository.count()).isEqualTo(1);
    }

    private static void assertExactlyOneStoredAndOneConflict(List<Object> results) {
        assertThat(results).filteredOn(r -> r instanceof ConflictException).hasSize(1);
        assertThat(results).filteredOn(r -> !(r instanceof Throwable)).hasSize(1);
    }

    // Starts both calls at the same instant; returns each call's result or the exception it threw.
    private static List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = List.of(
                executor.submit(() -> attempt(barrier, first)),
                executor.submit(() -> attempt(barrier, second))
            );
            var results = new java.util.ArrayList<Object>();
            for (var future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    throw new AssertionError("race task failed unexpectedly", e.getCause());
                }
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Object attempt(CyclicBarrier barrier, Callable<?> call) throws Exception {
        barrier.await();
        try {
            return call.call();
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static CreateStateEventRequest stateRequest(UUID eventId, long sequence) {
        return new CreateStateEventRequest(eventId, MachineState.DOWN, hoursAgo(1), null, sequence);
    }

    private static CreateProductionCountRequest countRequest(UUID eventId, long sequence) {
        return new CreateProductionCountRequest(eventId, 10L, 1L, hoursAgo(2), hoursAgo(1), sequence);
    }

    private static Instant hoursAgo(long hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }
}
