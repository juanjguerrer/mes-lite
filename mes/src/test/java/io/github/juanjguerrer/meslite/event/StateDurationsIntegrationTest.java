package io.github.juanjguerrer.meslite.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import com.jayway.jsonpath.JsonPath;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StateDurationsIntegrationTest {

    private static final long MINUTE_MS = Duration.ofMinutes(1).toMillis();
    private static final Instant FROM = at("08:00");
    private static final Instant TO = at("12:00");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductionLineRepository lineRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private MachineStateEventRepository eventRepository;

    private Machine press;
    private Machine labeler;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        ProductionLine line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        press = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line));
        labeler = machineRepository.save(new Machine("Labeler", "LBL-001", null, 300, line));
    }

    // Other integration test classes delete machines; leftover events would block them (FK RESTRICT).
    @AfterEach
    void cleanDatabase() {
        eventRepository.deleteAll();
        machineRepository.deleteAll();
        lineRepository.deleteAll();
    }

    @Test
    void clipsSegmentsToTheRangeUsingTheEventBeforeIt() throws Exception {
        save(press, MachineState.DOWN, at("07:30"));
        save(press, MachineState.RUNNING, at("09:00"));
        save(press, MachineState.IDLE, at("10:30"));
        save(press, MachineState.RUNNING, at("11:00"));
        save(labeler, MachineState.SETUP, at("09:30"));

        durations(press, FROM, TO)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.machineId").value(press.getId()))
            .andExpect(jsonPath("$.from").value(FROM.toString()))
            .andExpect(jsonPath("$.to").value(TO.toString()))
            .andExpect(jsonPath("$.durationsMs.RUNNING").value(150 * MINUTE_MS))
            .andExpect(jsonPath("$.durationsMs.DOWN").value(60 * MINUTE_MS))
            .andExpect(jsonPath("$.durationsMs.IDLE").value(30 * MINUTE_MS))
            .andExpect(jsonPath("$.durationsMs.SETUP").value(0))
            .andExpect(jsonPath("$.durationsMs.PLANNED_STOP").value(0))
            .andExpect(jsonPath("$.unknownMs").value(0));
    }

    @Test
    void machineWithoutHistoryIsUnknownForTheWholeRange() throws Exception {
        durations(press, FROM, TO)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unknownMs").value(240 * MINUTE_MS))
            .andExpect(jsonPath("$.durationsMs.length()").value(MachineState.values().length))
            .andExpect(jsonPath("$.durationsMs.RUNNING").value(0));
    }

    @Test
    void timeBeforeTheFirstEverEventIsUnknown() throws Exception {
        save(press, MachineState.RUNNING, at("10:00"));

        durations(press, FROM, TO)
            .andExpect(jsonPath("$.unknownMs").value(120 * MINUTE_MS))
            .andExpect(jsonPath("$.durationsMs.RUNNING").value(120 * MINUTE_MS));
    }

    @Test
    void eventExactlyAtFromStartsTheRange() throws Exception {
        save(press, MachineState.DOWN, at("07:00"));
        save(press, MachineState.RUNNING, at("08:00"));

        durations(press, FROM, TO)
            .andExpect(jsonPath("$.durationsMs.DOWN").value(0))
            .andExpect(jsonPath("$.durationsMs.RUNNING").value(240 * MINUTE_MS));
    }

    @Test
    void eventExactlyAtToIsNotCounted() throws Exception {
        save(press, MachineState.RUNNING, at("08:00"));
        save(press, MachineState.DOWN, at("12:00"));

        durations(press, FROM, TO)
            .andExpect(jsonPath("$.durationsMs.RUNNING").value(240 * MINUTE_MS))
            .andExpect(jsonPath("$.durationsMs.DOWN").value(0));
    }

    @Test
    void eventsInsertedOutOfOrderAreProcessedByOccurredAt() throws Exception {
        save(press, MachineState.RUNNING, at("11:00"));
        save(press, MachineState.DOWN, at("09:00"));
        save(press, MachineState.IDLE, at("08:00"));

        durations(press, FROM, TO)
            .andExpect(jsonPath("$.durationsMs.IDLE").value(60 * MINUTE_MS))
            .andExpect(jsonPath("$.durationsMs.DOWN").value(120 * MINUTE_MS))
            .andExpect(jsonPath("$.durationsMs.RUNNING").value(60 * MINUTE_MS));
    }

    @Test
    void futureToIsClippedToNow() throws Exception {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        save(press, MachineState.RUNNING, from);
        Instant requestedTo = Instant.now().plus(1, ChronoUnit.HOURS);

        String body = durations(press, from, requestedTo)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Instant effectiveTo = Instant.parse(JsonPath.read(body, "$.to"));
        long runningMs = ((Number) JsonPath.read(body, "$.durationsMs.RUNNING")).longValue();
        assertThat(effectiveTo).isBefore(requestedTo).isBeforeOrEqualTo(Instant.now());
        assertThat(runningMs).isEqualTo(Duration.between(from, effectiveTo).toMillis());
    }

    @Test
    void acceptsExactly31Days() throws Exception {
        durations(press, FROM.minus(31, ChronoUnit.DAYS), FROM)
            .andExpect(status().isOk());
    }

    @Test
    void rejectsRangeLongerThan31Days() throws Exception {
        durations(press, FROM.minus(31, ChronoUnit.DAYS).minusSeconds(1), FROM)
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void rejectsInvertedAndEmptyRanges() throws Exception {
        durations(press, TO, FROM).andExpect(status().isUnprocessableContent());
        durations(press, FROM, FROM).andExpect(status().isUnprocessableContent());
    }

    @Test
    void rejectsRangeStartingInTheFuture() throws Exception {
        Instant from = Instant.now().plus(1, ChronoUnit.HOURS);

        durations(press, from, from.plus(2, ChronoUnit.HOURS))
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void requiresFromAndTo() throws Exception {
        mvc.perform(get("/api/machines/" + press.getId() + "/state-durations").param("to", TO.toString()))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/machines/" + press.getId() + "/state-durations").param("from", FROM.toString()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForUnknownMachine() throws Exception {
        mvc.perform(get("/api/machines/999999/state-durations")
                .param("from", FROM.toString())
                .param("to", TO.toString()))
            .andExpect(status().isNotFound());
    }

    private ResultActions durations(Machine machine, Instant from, Instant to) throws Exception {
        return mvc.perform(get("/api/machines/" + machine.getId() + "/state-durations")
            .param("from", from.toString())
            .param("to", to.toString()));
    }

    private final AtomicLong sequence = new AtomicLong();

    private void save(Machine machine, MachineState state, Instant occurredAt) {
        eventRepository.save(new MachineStateEvent(machine, UUID.randomUUID(), state, occurredAt, null, sequence.incrementAndGet()));
    }

    private static Instant at(String hhmm) {
        return Instant.parse("2026-10-19T" + hhmm + ":00Z");
    }
}
