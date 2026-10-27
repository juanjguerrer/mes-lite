package io.github.juanjguerrer.meslite.oee;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
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
import org.springframework.test.web.servlet.ResultActions;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.event.MachineState;
import io.github.juanjguerrer.meslite.event.MachineStateEvent;
import io.github.juanjguerrer.meslite.event.MachineStateEventRepository;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.production.ProductionCount;
import io.github.juanjguerrer.meslite.production.ProductionCountRepository;
import io.github.juanjguerrer.meslite.shift.Shift;
import io.github.juanjguerrer.meslite.shift.ShiftRepository;

/**
 * The press example from the guide, seeded in the database: shift A (06:00-14:00 plant time, UTC-6)
 * on 2026-10-19 = 12:00Z-20:00Z with 360 min RUNNING, 30 min PLANNED_STOP, 90 min DOWN,
 * 1470 good and 30 scrap pieces, ideal cycle 12 s -> OEE 0.6533.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OeeIntegrationTest {

    private static final String DATE = "2026-10-19";
    private static final Instant SHIFT_FROM = Instant.parse("2026-10-19T12:00:00Z");
    private static final Instant SHIFT_TO = Instant.parse("2026-10-19T20:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductionLineRepository lineRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private MachineStateEventRepository eventRepository;

    @Autowired
    private ProductionCountRepository countRepository;

    private ProductionLine line;
    private Machine press;
    private Machine manualStation;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        press = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12_000, line));
        manualStation = machineRepository.save(new Machine("Manual station", "MAN-001", null, null, line));
        shiftRepository.save(new Shift(line, "A", "Morning", LocalTime.of(6, 0), LocalTime.of(14, 0)));
        seedPressExample(press);
    }

    // Other integration test classes delete machines and lines; leftovers would block them (FK RESTRICT).
    @AfterEach
    void cleanDatabase() {
        lineRepository.findAll().forEach(l -> {
            l.setReferenceMachine(null);
            lineRepository.save(l);
        });
        countRepository.deleteAll();
        eventRepository.deleteAll();
        shiftRepository.deleteAll();
        machineRepository.deleteAll();
        lineRepository.deleteAll();
    }

    // --- machine OEE ---

    @Test
    void machineOeeByShiftMatchesTheHandCalculation() throws Exception {
        machineOeeByShift(press.getId(), "a")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.machineId").value(press.getId()))
            .andExpect(jsonPath("$.shiftCode").value("A"))
            .andExpect(jsonPath("$.from").value(SHIFT_FROM.toString()))
            .andExpect(jsonPath("$.to").value(SHIFT_TO.toString()))
            .andExpect(jsonPath("$.plannedMs").value(minutes(450)))
            .andExpect(jsonPath("$.runningMs").value(minutes(360)))
            .andExpect(jsonPath("$.goodCount").value(1470))
            .andExpect(jsonPath("$.scrapCount").value(30))
            .andExpect(jsonPath("$.availability").value(0.8))
            .andExpect(jsonPath("$.performance").value(0.8333))
            .andExpect(jsonPath("$.quality").value(0.98))
            .andExpect(jsonPath("$.oee").value(0.6533))
            .andExpect(jsonPath("$.dataCoverage").value(1.0))
            .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void machineOeeByRangeEqualsTheShiftResult() throws Exception {
        machineOeeByRange(press.getId(), SHIFT_FROM, SHIFT_TO)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftCode").doesNotExist())
            .andExpect(jsonPath("$.oee").value(0.6533));
    }

    @Test
    void machineWithoutIdealCycleHasNoOeeButExplainsWhy() throws Exception {
        seedPressExample(manualStation);

        machineOeeByShift(manualStation.getId(), "A")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availability").value(0.8))
            .andExpect(jsonPath("$.quality").value(0.98))
            .andExpect(jsonPath("$.performance").doesNotExist())
            .andExpect(jsonPath("$.oee").doesNotExist())
            .andExpect(jsonPath("$.warnings", hasItem(containsString("ideal cycle time"))));
    }

    @Test
    void returns404ForUnknownMachine() throws Exception {
        machineOeeByShift(999_999L, "A").andExpect(status().isNotFound());
    }

    @Test
    void returns404ForUnknownShift() throws Exception {
        machineOeeByShift(press.getId(), "Z").andExpect(status().isNotFound());
    }

    // --- parameter combinations: all are malformed requests ---

    @Test
    void returns400WhenRangeAndShiftAreBothGiven() throws Exception {
        mvc.perform(get("/api/machines/" + press.getId() + "/oee")
                .param("from", SHIFT_FROM.toString())
                .param("to", SHIFT_TO.toString())
                .param("shiftCode", "A")
                .param("date", DATE))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenOnlyHalfOfTheRangeIsGiven() throws Exception {
        mvc.perform(get("/api/machines/" + press.getId() + "/oee").param("from", SHIFT_FROM.toString()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenNoRangeNorShiftIsGiven() throws Exception {
        mvc.perform(get("/api/machines/" + press.getId() + "/oee"))
            .andExpect(status().isBadRequest());
    }

    // --- line OEE ---

    @Test
    void lineOeeIsTheOeeOfItsReferenceMachine() throws Exception {
        line.setReferenceMachine(press);
        lineRepository.save(line);

        mvc.perform(get("/api/lines/" + line.getId() + "/oee").param("shiftCode", "A").param("date", DATE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.machineId").value(press.getId()))
            .andExpect(jsonPath("$.oee").value(0.6533));
    }

    @Test
    void lineWithoutReferenceMachineReturns422() throws Exception {
        mvc.perform(get("/api/lines/" + line.getId() + "/oee").param("shiftCode", "A").param("date", DATE))
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns404ForUnknownLine() throws Exception {
        mvc.perform(get("/api/lines/999999/oee").param("shiftCode", "A").param("date", DATE))
            .andExpect(status().isNotFound());
    }

    private void seedPressExample(Machine machine) {
        event(machine, MachineState.RUNNING, SHIFT_FROM);                          // 06:00-09:00 running (180)
        event(machine, MachineState.PLANNED_STOP, SHIFT_FROM.plus(hours(3)));        // 09:00-09:30 planned stop (30)
        event(machine, MachineState.DOWN, SHIFT_FROM.plus(minutesDuration(210)));    // 09:30-11:00 down (90)
        event(machine, MachineState.RUNNING, SHIFT_FROM.plus(hours(5)));             // 11:00-14:00 running (180)
        count(machine, 735, 15, SHIFT_FROM, SHIFT_FROM.plus(hours(4)));
        count(machine, 735, 15, SHIFT_FROM.plus(hours(4)), SHIFT_TO);               // ends exactly at "to": included
    }

    private final AtomicLong sequence = new AtomicLong();

    private void event(Machine machine, MachineState state, Instant occurredAt) {
        eventRepository.save(new MachineStateEvent(machine, UUID.randomUUID(), state, occurredAt, null, sequence.incrementAndGet()));
    }

    private void count(Machine machine, long good, long scrap, Instant start, Instant end) {
        countRepository.save(new ProductionCount(machine, UUID.randomUUID(), good, scrap, start, end, sequence.incrementAndGet()));
    }

    private ResultActions machineOeeByShift(Long machineId, String shiftCode) throws Exception {
        return mvc.perform(get("/api/machines/" + machineId + "/oee").param("shiftCode", shiftCode).param("date", DATE));
    }

    private ResultActions machineOeeByRange(Long machineId, Instant from, Instant to) throws Exception {
        return mvc.perform(get("/api/machines/" + machineId + "/oee")
            .param("from", from.toString())
            .param("to", to.toString()));
    }

    private static Duration hours(long hours) {
        return Duration.ofHours(hours);
    }

    private static Duration minutesDuration(long minutes) {
        return Duration.ofMinutes(minutes);
    }

    private static long minutes(long minutes) {
        return Duration.ofMinutes(minutes).toMillis();
    }
}
