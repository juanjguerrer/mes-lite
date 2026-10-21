package io.github.juanjguerrer.meslite.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MachineStateEventIntegrationTest {

    @Autowired
    private MockMvc mvc;

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
        machineRepository.save(new Machine("Labeler", "LBL-001", null, 300, line));
    }

    // Other integration test classes delete machines; leftover events would block them (FK RESTRICT).
    @AfterEach
    void cleanDatabase() {
        eventRepository.deleteAll();
        machineRepository.deleteAll();
        lineRepository.deleteAll();
    }

    @Test
    void createsEventAndExposesItsLocation() throws Exception {
        UUID eventId = UUID.randomUUID();

        String location = ingest("line-01", "prs-003", body(eventId, "DOWN", hoursAgo(1), "Jammed die"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern(".*/api/events/\\d+")))
            .andExpect(jsonPath("$.eventId").value(eventId.toString()))
            .andExpect(jsonPath("$.machineId").value(press.getId()))
            .andExpect(jsonPath("$.state").value("DOWN"))
            .andExpect(jsonPath("$.reason").value("Jammed die"))
            .andExpect(jsonPath("$.receivedAt").exists())
            .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value(eventId.toString()));
    }

    @Test
    void retryWithSamePayloadReturns200AndDoesNotDuplicate() throws Exception {
        UUID eventId = UUID.randomUUID();
        String body = body(eventId, "RUNNING", hoursAgo(1), null);

        String firstId = ingest("LINE-01", "PRS-003", body)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        ingest("LINE-01", "PRS-003", body)
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Location"))
            .andExpect(jsonPath("$.id").value(Long.valueOf(firstId)));

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void retryWithNanosecondPrecisionIsStillTheSameEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        String occurredAt = hoursAgo(1).truncatedTo(ChronoUnit.SECONDS).plusNanos(123_456_789).toString();
        String body = body(eventId, "IDLE", occurredAt, null);

        ingest("LINE-01", "PRS-003", body).andExpect(status().isCreated());
        ingest("LINE-01", "PRS-003", body).andExpect(status().isOk());

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void reusedEventIdWithDifferentStateReturns409() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = hoursAgo(1);

        ingest("LINE-01", "PRS-003", body(eventId, "DOWN", occurredAt, null)).andExpect(status().isCreated());

        ingest("LINE-01", "PRS-003", body(eventId, "RUNNING", occurredAt, null))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("eventId " + eventId + " was already used for a different event"));
    }

    @Test
    void reusedEventIdOnAnotherMachineReturns409() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = hoursAgo(1);

        ingest("LINE-01", "PRS-003", body(eventId, "DOWN", occurredAt, null)).andExpect(status().isCreated());

        ingest("LINE-01", "LBL-001", body(eventId, "DOWN", occurredAt, null))
            .andExpect(status().isConflict());

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void returns404ForUnknownMachine() throws Exception {
        ingest("LINE-01", "XXX-999", body(UUID.randomUUID(), "DOWN", hoursAgo(1), null))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Machine with line code LINE-01 and machine code XXX-999 not found"));
    }

    @Test
    void returns404ForKnownMachineCodeOnWrongLine() throws Exception {
        lineRepository.save(new ProductionLine("Paint", "LINE-02", null));

        ingest("LINE-02", "PRS-003", body(UUID.randomUUID(), "DOWN", hoursAgo(1), null))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns422ForEventTooFarInTheFuture() throws Exception {
        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), "DOWN", Instant.now().plus(1, ChronoUnit.HOURS), null))
            .andExpect(status().isUnprocessableContent());

        assertThat(eventRepository.count()).isZero();
    }

    @Test
    void returns400ForUnknownState() throws Exception {
        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), "BROKEN", hoursAgo(1), null))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WithFieldErrorsForMissingFields() throws Exception {
        ingest("LINE-01", "PRS-003", "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.eventId").exists())
            .andExpect(jsonPath("$.errors.state").exists())
            .andExpect(jsonPath("$.errors.occurredAt").exists());
    }

    @Test
    void returns400ForReasonLongerThanColumn() throws Exception {
        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), "DOWN", hoursAgo(1), "x".repeat(256)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.reason").exists());
    }

    @Test
    void returns404ForUnknownEvent() throws Exception {
        mvc.perform(get("/api/events/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void machineWithEventsCannotBeDeleted() throws Exception {
        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), "RUNNING", hoursAgo(1), null)).andExpect(status().isCreated());

        mvc.perform(delete("/api/machines/" + press.getId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("The machine cannot be deleted because it has associated state events."));

        assertThat(machineRepository.existsById(press.getId())).isTrue();
    }

    private ResultActions ingest(String lineCode, String machineCode, String body) throws Exception {
        return mvc.perform(post("/api/lines/" + lineCode + "/machines/" + machineCode + "/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private static String body(UUID eventId, String state, Object occurredAt, String reason) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        return """
            {"eventId": "%s", "state": "%s", "occurredAt": "%s", "reason": %s}
            """.formatted(eventId, state, occurredAt, reasonJson);
    }

    private static Instant hoursAgo(int hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }
}
