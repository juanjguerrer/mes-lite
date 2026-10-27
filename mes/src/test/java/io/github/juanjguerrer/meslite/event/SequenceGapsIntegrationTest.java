package io.github.juanjguerrer.meslite.event;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.production.ProductionCount;
import io.github.juanjguerrer.meslite.production.ProductionCountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SequenceGapsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductionLineRepository lineRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private MachineStateEventRepository eventRepository;

    @Autowired
    private ProductionCountRepository countRepository;

    private Machine press;
    private Machine labeler;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        ProductionLine line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        press = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line));
        labeler = machineRepository.save(new Machine("Labeler", "LBL-001", null, 300, line));
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
    void machineWithoutEventsHasNoGaps() throws Exception {
        gaps(press)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.machineId").value(press.getId()))
            .andExpect(jsonPath("$.totalMissing").value(0))
            .andExpect(jsonPath("$.gaps").isEmpty());
    }

    @Test
    void contiguousSequencesAcrossBothTablesHaveNoGaps() throws Exception {
        state(press, 1L);
        count(press, 2L);
        state(press, 3L);
        count(press, 4L);

        gaps(press)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalMissing").value(0))
            .andExpect(jsonPath("$.gaps").isEmpty());
    }

    @Test
    void gapBetweenStateEventAndProductionCountIsReported() throws Exception {
        state(press, 1L);
        count(press, 4L);
        state(press, 5L);

        gaps(press)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalMissing").value(2))
            .andExpect(jsonPath("$.gaps.length()").value(1))
            .andExpect(jsonPath("$.gaps[0].fromSequence").value(1))
            .andExpect(jsonPath("$.gaps[0].toSequence").value(4))
            .andExpect(jsonPath("$.gaps[0].missing").value(2));
    }

    @Test
    void severalGapsAreListedInOrderAndSummed() throws Exception {
        count(press, 7L);
        state(press, 1L);
        state(press, 3L);

        gaps(press)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalMissing").value(4))
            .andExpect(jsonPath("$.gaps.length()").value(2))
            .andExpect(jsonPath("$.gaps[0].fromSequence").value(1))
            .andExpect(jsonPath("$.gaps[0].toSequence").value(3))
            .andExpect(jsonPath("$.gaps[0].missing").value(1))
            .andExpect(jsonPath("$.gaps[1].fromSequence").value(3))
            .andExpect(jsonPath("$.gaps[1].toSequence").value(7))
            .andExpect(jsonPath("$.gaps[1].missing").value(3));
    }

    @Test
    void lateEventClosesTheGap() throws Exception {
        state(press, 1L);
        state(press, 3L);

        gaps(press).andExpect(jsonPath("$.totalMissing").value(1));

        count(press, 2L);

        gaps(press)
            .andExpect(jsonPath("$.totalMissing").value(0))
            .andExpect(jsonPath("$.gaps").isEmpty());
    }

    @Test
    void sequencesOfOtherMachinesAreNotMixed() throws Exception {
        state(press, 1L);
        state(press, 2L);
        state(labeler, 1L);
        state(labeler, 5L);

        gaps(press)
            .andExpect(jsonPath("$.totalMissing").value(0))
            .andExpect(jsonPath("$.gaps").isEmpty());

        gaps(labeler)
            .andExpect(jsonPath("$.totalMissing").value(3))
            .andExpect(jsonPath("$.gaps[0].fromSequence").value(1))
            .andExpect(jsonPath("$.gaps[0].toSequence").value(5));
    }

    @Test
    void eventsStoredWithoutSequenceAreIgnored() throws Exception {
        state(press, null);
        count(press, null);
        state(press, 1L);
        state(press, 2L);

        gaps(press)
            .andExpect(jsonPath("$.totalMissing").value(0))
            .andExpect(jsonPath("$.gaps").isEmpty());
    }

    @Test
    void returns404ForUnknownMachine() throws Exception {
        mvc.perform(get("/api/machines/999999/sequence-gaps"))
            .andExpect(status().isNotFound());
    }

    private ResultActions gaps(Machine machine) throws Exception {
        return mvc.perform(get("/api/machines/{id}/sequence-gaps", machine.getId()));
    }

    private void state(Machine machine, Long sequence) {
        eventRepository.save(new MachineStateEvent(
            machine, UUID.randomUUID(), MachineState.RUNNING, hoursAgo(1), null, sequence));
    }

    private void count(Machine machine, Long sequence) {
        countRepository.save(new ProductionCount(
            machine, UUID.randomUUID(), 10, 1, hoursAgo(2), hoursAgo(1), sequence));
    }

    private static Instant hoursAgo(long hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }
}
