package io.github.juanjguerrer.meslite.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.matchesPattern;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.event.MachineStateEventRepository;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.shift.ShiftRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductionCountIntegrationTest {

    private static final Instant T0800 = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant T0900 = Instant.parse("2026-10-19T09:00:00Z");
    private static final Instant T1000 = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant T1100 = Instant.parse("2026-10-19T11:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductionCountRepository countRepository;

    @Autowired
    private MachineStateEventRepository eventRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private ProductionLineRepository lineRepository;

    private Machine press;
    private Machine labeler;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        ProductionLine line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        press = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line));
        labeler = machineRepository.save(new Machine("Labeler", "LBL-001", null, 300, line));
    }

    // Other integration test classes delete machines and lines; leftovers would block them (FK RESTRICT).
    @AfterEach
    void cleanDatabase() {
        countRepository.deleteAll();
        eventRepository.deleteAll();
        shiftRepository.deleteAll();
        machineRepository.deleteAll();
        lineRepository.deleteAll();
    }

    // --- ingestion ---

    @Test
    void createsCountAndExposesItsLocation() throws Exception {
        UUID eventId = UUID.randomUUID();

        String location = ingest("line-01", "prs-003", body(eventId, 95, 5, T0800, T0900))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern(".*/api/production-counts/\\d+")))
            .andExpect(jsonPath("$.machineId").value(press.getId()))
            .andExpect(jsonPath("$.goodCount").value(95))
            .andExpect(jsonPath("$.scrapCount").value(5))
            .andExpect(jsonPath("$.intervalEnd").value(T0900.toString()))
            .andExpect(jsonPath("$.receivedAt").exists())
            .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value(eventId.toString()));
    }

    @Test
    void retryWithSamePayloadReturns200AndDoesNotDuplicate() throws Exception {
        String body = body(UUID.randomUUID(), 95, 5, T0800, T0900);

        ingest("LINE-01", "PRS-003", body).andExpect(status().isCreated());
        ingest("LINE-01", "PRS-003", body)
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("Location"));

        assertThat(countRepository.count()).isEqualTo(1);
    }

    @Test
    void retryWithNanosecondPrecisionIsStillTheSameCount() throws Exception {
        String body = body(UUID.randomUUID(), 10, 0, T0800.plusNanos(123_456_789), T0900.plusNanos(987_654_321));

        ingest("LINE-01", "PRS-003", body).andExpect(status().isCreated());
        ingest("LINE-01", "PRS-003", body).andExpect(status().isOk());
    }

    @Test
    void reusedEventIdWithDifferentDataReturns409() throws Exception {
        UUID eventId = UUID.randomUUID();

        ingest("LINE-01", "PRS-003", body(eventId, 95, 5, T0800, T0900)).andExpect(status().isCreated());

        ingest("LINE-01", "PRS-003", body(eventId, 96, 5, T0800, T0900))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("eventId " + eventId + " already exists with different data."));
    }

    @Test
    void sequenceAlreadyUsedByAnotherCountReturns409() throws Exception {
        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), 95, 5, T0800, T0900)).andExpect(status().isCreated());

        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), 40, 2, T0900, T1000))
            .andExpect(status().isConflict());

        assertThat(countRepository.count()).isEqualTo(1);
    }

    @Test
    void sameSequenceOnAnotherMachineIsAccepted() throws Exception {
        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), 95, 5, T0800, T0900)).andExpect(status().isCreated());
        ingest("LINE-01", "LBL-001", body(UUID.randomUUID(), 95, 5, T0800, T0900)).andExpect(status().isCreated());

        assertThat(countRepository.count()).isEqualTo(2);
    }

    @Test
    void returns404ForUnknownMachine() throws Exception {
        ingest("LINE-01", "XXX-999", body(UUID.randomUUID(), 1, 0, T0800, T0900))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns422WhenIntervalIsEmptyOrInverted() throws Exception {
        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), 1, 0, T0900, T0900))
            .andExpect(status().isUnprocessableContent());

        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), 1, 0, T0900, T0800))
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns422WhenIntervalEndsTooFarInTheFuture() throws Exception {
        // Starts in the past but ends an hour from now: the end is what must not be in the future.
        Instant now = Instant.now();
        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), 1, 0, now.minus(1, ChronoUnit.MINUTES), now.plus(1, ChronoUnit.HOURS)))
            .andExpect(status().isUnprocessableContent());

        assertThat(countRepository.count()).isZero();
    }

    @Test
    void returns400ForNegativeCounts() throws Exception {
        ingest("LINE-01", "PRS-003", body(UUID.randomUUID(), -1, 0, T0800, T0900))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.goodCount").exists());
    }

    @Test
    void returns400WithFieldErrorsForMissingFields() throws Exception {
        ingest("LINE-01", "PRS-003", "{}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.eventId").exists())
            .andExpect(jsonPath("$.errors.goodCount").exists())
            .andExpect(jsonPath("$.errors.scrapCount").exists())
            .andExpect(jsonPath("$.errors.intervalStart").exists())
            .andExpect(jsonPath("$.errors.intervalEnd").exists())
            .andExpect(jsonPath("$.errors.sequence").exists());
    }

    @Test
    void reusedEventIdWithDifferentSequenceReturns409() throws Exception {
        UUID eventId = UUID.randomUUID();

        ingest("LINE-01", "PRS-003", body(eventId, 95, 5, T0800, T0900)).andExpect(status().isCreated());

        ingest("LINE-01", "PRS-003", body(eventId, 95, 5, T0800, T0900).replace("\"sequence\": 1", "\"sequence\": 2"))
            .andExpect(status().isConflict());
    }

    @Test
    void returns404ForUnknownCount() throws Exception {
        mvc.perform(get("/api/production-counts/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void machineWithCountsCannotBeDeleted() throws Exception {
        save(press, 1, 0, T0800, T0900);

        mvc.perform(delete("/api/machines/" + press.getId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("The machine cannot be deleted because it has associated production counts."));
    }

    // --- totals ---

    @Test
    void totalsIncludeCountsEndingInsideTheRangeIncludingTheEnd() throws Exception {
        save(press, 100, 1, T0800.minus(1, ChronoUnit.HOURS), T0800);  // ends exactly at from: previous range
        save(press, 50, 2, T0800, T0900);                              // inside
        save(press, 40, 3, T0900, T1000);                              // ends exactly at to: included
        save(press, 30, 4, T1000, T1100);                              // after the range
        save(labeler, 999, 9, T0800, T0900);                           // another machine

        totals(press.getId(), T0800, T1000)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.goodCount").value(90))
            .andExpect(jsonPath("$.scrapCount").value(5))
            .andExpect(jsonPath("$.totalCount").value(95))
            .andExpect(jsonPath("$.from").value(T0800.toString()))
            .andExpect(jsonPath("$.to").value(T1000.toString()));
    }

    @Test
    void adjacentRangesNeverCountTheSamePiecesTwice() throws Exception {
        save(press, 50, 2, T0800, T0900);
        save(press, 40, 3, T0900, T1000);

        totals(press.getId(), T0800, T0900).andExpect(jsonPath("$.totalCount").value(52));
        totals(press.getId(), T0900, T1000).andExpect(jsonPath("$.totalCount").value(43));
    }

    @Test
    void totalsAreZeroWithoutCounts() throws Exception {
        totals(press.getId(), T0800, T1000)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.goodCount").value(0))
            .andExpect(jsonPath("$.scrapCount").value(0))
            .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void totalsReturn404ForUnknownMachine() throws Exception {
        totals(999999L, T0800, T1000).andExpect(status().isNotFound());
    }

    @Test
    void totalsReject422InvertedRange() throws Exception {
        totals(press.getId(), T1000, T0800).andExpect(status().isUnprocessableContent());
    }

    @Test
    void totalsRequireBothEnds() throws Exception {
        mvc.perform(get("/api/machines/" + press.getId() + "/production-counts/totals").param("from", T0800.toString()))
            .andExpect(status().isBadRequest());
    }

    // --- hourly ---

    @Test
    void hourlySumsCountsEndingInTheSameHour() throws Exception {
        save(press, 10, 1, T0800.plus(10, ChronoUnit.MINUTES), T0800.plus(20, ChronoUnit.MINUTES));
        save(press, 20, 2, T0800.plus(20, ChronoUnit.MINUTES), T0800.plus(40, ChronoUnit.MINUTES));

        hourly(press.getId(), T0800, T0900)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.machineId").value(press.getId()))
            .andExpect(jsonPath("$.from").value(T0800.toString()))
            .andExpect(jsonPath("$.to").value(T0900.toString()))
            .andExpect(jsonPath("$.buckets.length()").value(1))
            .andExpect(jsonPath("$.buckets[0].bucketStart").value(T0800.toString()))
            .andExpect(jsonPath("$.buckets[0].goodCount").value(30))
            .andExpect(jsonPath("$.buckets[0].scrapCount").value(3));
    }

    @Test
    void hourlyPutsACountEndingExactlyOnTheHourInThePreviousBucket() throws Exception {
        save(press, 50, 2, T0800, T0900);
        save(press, 40, 3, T0900, T1000);

        hourly(press.getId(), T0800, T1000)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets.length()").value(2))
            .andExpect(jsonPath("$.buckets[0].bucketStart").value(T0800.toString()))
            .andExpect(jsonPath("$.buckets[0].goodCount").value(50))
            .andExpect(jsonPath("$.buckets[0].scrapCount").value(2))
            .andExpect(jsonPath("$.buckets[1].bucketStart").value(T0900.toString()))
            .andExpect(jsonPath("$.buckets[1].goodCount").value(40))
            .andExpect(jsonPath("$.buckets[1].scrapCount").value(3));
    }

    @Test
    void hourlyReturnsHoursWithoutCountsAsZeros() throws Exception {
        save(press, 50, 2, T0800, T0900);
        save(press, 30, 4, T1000, T1100);

        hourly(press.getId(), T0800, T1100)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets.length()").value(3))
            .andExpect(jsonPath("$.buckets[1].bucketStart").value(T0900.toString()))
            .andExpect(jsonPath("$.buckets[1].goodCount").value(0))
            .andExpect(jsonPath("$.buckets[1].scrapCount").value(0));
    }

    @Test
    void hourlyIgnoresCountsOutsideTheRangeAndOfOtherMachines() throws Exception {
        save(press, 100, 1, T0800.minus(1, ChronoUnit.HOURS), T0800);  // ends exactly at from
        save(press, 50, 2, T0800, T0900);                              // inside
        save(press, 30, 4, T0900, T1000);                              // after the range
        save(labeler, 999, 9, T0800, T0900);                           // another machine

        hourly(press.getId(), T0800, T0900)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets.length()").value(1))
            .andExpect(jsonPath("$.buckets[0].goodCount").value(50))
            .andExpect(jsonPath("$.buckets[0].scrapCount").value(2));
    }

    @Test
    void hourlyFirstBucketIsPartialWhenFromIsNotOnTheHour() throws Exception {
        Instant from = T0800.plus(30, ChronoUnit.MINUTES);
        save(press, 10, 1, T0800.plus(10, ChronoUnit.MINUTES), T0800.plus(20, ChronoUnit.MINUTES)); // before from
        save(press, 20, 2, T0800.plus(40, ChronoUnit.MINUTES), T0800.plus(50, ChronoUnit.MINUTES)); // after from

        hourly(press.getId(), from, T0900)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets.length()").value(1))
            .andExpect(jsonPath("$.buckets[0].bucketStart").value(T0800.toString()))
            .andExpect(jsonPath("$.buckets[0].goodCount").value(20))
            .andExpect(jsonPath("$.buckets[0].scrapCount").value(2));
    }

    @Test
    void hourlyReturns404ForUnknownMachine() throws Exception {
        hourly(999999L, T0800, T1000).andExpect(status().isNotFound());
    }

    @Test
    void hourlyRejects422InvertedOrEmptyRange() throws Exception {
        hourly(press.getId(), T1000, T0800).andExpect(status().isUnprocessableContent());
        hourly(press.getId(), T0800, T0800).andExpect(status().isUnprocessableContent());
    }

    @Test
    void hourlyRejects422RangeLongerThan31Days() throws Exception {
        hourly(press.getId(), T0800, T0800.plus(32, ChronoUnit.DAYS)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void hourlyRequiresBothEnds() throws Exception {
        mvc.perform(get("/api/machines/" + press.getId() + "/production-counts/hourly").param("from", T0800.toString()))
            .andExpect(status().isBadRequest());
    }

    private ResultActions hourly(Long machineId, Instant from, Instant to) throws Exception {
        return mvc.perform(get("/api/machines/" + machineId + "/production-counts/hourly")
            .param("from", from.toString())
            .param("to", to.toString()));
    }

    private ResultActions ingest(String lineCode, String machineCode, String body) throws Exception {
        return mvc.perform(post("/api/lines/" + lineCode + "/machines/" + machineCode + "/counts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private ResultActions totals(Long machineId, Instant from, Instant to) throws Exception {
        return mvc.perform(get("/api/machines/" + machineId + "/production-counts/totals")
            .param("from", from.toString())
            .param("to", to.toString()));
    }

    private final AtomicLong sequence = new AtomicLong();

    private void save(Machine machine, long good, long scrap, Instant start, Instant end) {
        countRepository.save(new ProductionCount(machine, UUID.randomUUID(), good, scrap, start, end, sequence.incrementAndGet()));
    }

    private static String body(UUID eventId, long good, long scrap, Instant start, Instant end) {
        return """
            {"eventId": "%s", "goodCount": %d, "scrapCount": %d, "intervalStart": "%s", "intervalEnd": "%s", "sequence": 1}
            """.formatted(eventId, good, scrap, start, end);
    }
}
