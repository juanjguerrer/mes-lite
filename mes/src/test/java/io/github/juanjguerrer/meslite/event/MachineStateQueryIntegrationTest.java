package io.github.juanjguerrer.meslite.event;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MachineStateQueryIntegrationTest {

    private static final Instant T0800 = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant T0900 = Instant.parse("2026-10-19T09:00:00Z");
    private static final Instant T1000 = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant T1100 = Instant.parse("2026-10-19T11:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductionLineRepository lineRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private MachineStateEventRepository eventRepository;

    private ProductionLine line;
    private Machine press;
    private Machine labeler;
    private Machine filler;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        ProductionLine otherLine = lineRepository.save(new ProductionLine("Bottling", "LINE-02", null));
        press = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line));
        labeler = machineRepository.save(new Machine("Labeler", "LBL-001", null, 300, line));
        filler = machineRepository.save(new Machine("Filler", "FIL-001", null, 100, otherLine));
    }

    // Other integration test classes delete machines; leftover events would block them (FK RESTRICT).
    @AfterEach
    void cleanDatabase() {
        eventRepository.deleteAll();
        machineRepository.deleteAll();
        lineRepository.deleteAll();
    }

    // --- history ---

    @Test
    void historyIsSortedByOccurredAtDescendingNotByArrival() throws Exception {
        // Inserted out of order: the 08:00 event "arrives" last.
        save(press, MachineState.RUNNING, T1000);
        save(press, MachineState.DOWN, T0900);
        save(press, MachineState.SETUP, T0800);
        save(filler, MachineState.DOWN, T0900);

        mvc.perform(get("/api/machines/" + press.getId() + "/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.content[*].state", contains("RUNNING", "DOWN", "SETUP")));
    }

    @Test
    void historyRangeIncludesFromAndExcludesTo() throws Exception {
        save(press, MachineState.SETUP, T0800);
        save(press, MachineState.DOWN, T0900);
        save(press, MachineState.RUNNING, T1000);
        save(press, MachineState.IDLE, T1100);

        mvc.perform(get("/api/machines/" + press.getId() + "/events")
                .param("from", T0900.toString())
                .param("to", T1100.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].state", contains("RUNNING", "DOWN")));
    }

    @Test
    void historyIsPaginated() throws Exception {
        save(press, MachineState.SETUP, T0800);
        save(press, MachineState.DOWN, T0900);
        save(press, MachineState.RUNNING, T1000);

        mvc.perform(get("/api/machines/" + press.getId() + "/events").param("size", "2").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].state", contains("SETUP")))
            .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void historyRejectsInvertedRange() throws Exception {
        mvc.perform(get("/api/machines/" + press.getId() + "/events")
                .param("from", T1000.toString())
                .param("to", T0800.toString()))
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void historyReturns404ForUnknownMachine() throws Exception {
        mvc.perform(get("/api/machines/999999/events"))
            .andExpect(status().isNotFound());
    }

    // --- current state ---

    @Test
    void currentStateIsTheLatestByOccurredAtEvenIfAnOlderEventArrivesLate() throws Exception {
        save(press, MachineState.RUNNING, T1000);
        save(press, MachineState.DOWN, T0900);

        mvc.perform(get("/api/machines/" + press.getId() + "/state"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.machineId").value(press.getId()))
            .andExpect(jsonPath("$.machineCode").value("PRS-003"))
            .andExpect(jsonPath("$.state").value("RUNNING"))
            .andExpect(jsonPath("$.since").value(T1000.toString()));
    }

    @Test
    void currentStateBreaksTiesByInsertionOrder() throws Exception {
        save(press, MachineState.DOWN, T0900);
        save(press, MachineState.RUNNING, T0900);

        mvc.perform(get("/api/machines/" + press.getId() + "/state"))
            .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    void currentStateIsNullForMachineWithoutEvents() throws Exception {
        mvc.perform(get("/api/machines/" + press.getId() + "/state"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.machineCode").value("PRS-003"))
            .andExpect(jsonPath("$.state").doesNotExist())
            .andExpect(jsonPath("$.since").doesNotExist());
    }

    @Test
    void currentStateReturns404ForUnknownMachine() throws Exception {
        mvc.perform(get("/api/machines/999999/state"))
            .andExpect(status().isNotFound());
    }

    // --- current states of a line ---

    @Test
    void lineStatesIncludeEveryMachineOfTheLineAndOnlyThose() throws Exception {
        save(press, MachineState.DOWN, T0800);
        save(press, MachineState.RUNNING, T1000);
        save(press, MachineState.RUNNING, T1000);
        save(filler, MachineState.DOWN, T1100);

        mvc.perform(get("/api/lines/" + line.getId() + "/machines/states"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].machineCode", containsInAnyOrder("PRS-003", "LBL-001")))
            .andExpect(jsonPath("$[?(@.machineCode == 'PRS-003')].state", contains("RUNNING")))
            .andExpect(jsonPath("$[?(@.machineCode == 'PRS-003')].since", contains(T1000.toString())))
            .andExpect(jsonPath("$[?(@.machineCode == 'LBL-001')].state", contains((Object) null)))
            .andExpect(jsonPath("$[?(@.machineCode == 'LBL-001')].machineId", contains(labeler.getId().intValue())));
    }

    @Test
    void lineStatesReturn404ForUnknownLine() throws Exception {
        mvc.perform(get("/api/lines/999999/machines/states"))
            .andExpect(status().isNotFound());
    }

    private final AtomicLong sequence = new AtomicLong();

    private void save(Machine machine, MachineState state, Instant occurredAt) {
        eventRepository.save(new MachineStateEvent(machine, UUID.randomUUID(), state, occurredAt, null, sequence.incrementAndGet()));
    }
}
