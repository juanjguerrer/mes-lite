package io.github.juanjguerrer.meslite.production;

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
import io.github.juanjguerrer.meslite.event.MachineStateEventRepository;
import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.github.juanjguerrer.meslite.line.ProductionLineRepository;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;
import io.github.juanjguerrer.meslite.shift.ShiftRepository;

// A plant zone with a half-hour offset: hourly buckets start at :30 UTC only if the
// truncation uses the plant zone and not UTC.
@SpringBootTest(properties = "mes.plant.zone-id=Asia/Kolkata")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductionCountHourlyZoneTest {

    private static final Instant T0730 = Instant.parse("2026-10-19T07:30:00Z");
    private static final Instant T0800 = Instant.parse("2026-10-19T08:00:00Z");
    private static final Instant T0830 = Instant.parse("2026-10-19T08:30:00Z");
    private static final Instant T0845 = Instant.parse("2026-10-19T08:45:00Z");
    private static final Instant T0930 = Instant.parse("2026-10-19T09:30:00Z");
    private static final Instant T0945 = Instant.parse("2026-10-19T09:45:00Z");
    private static final Instant T1000 = Instant.parse("2026-10-19T10:00:00Z");

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

    private final AtomicLong sequence = new AtomicLong();
    private Machine press;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        ProductionLine line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        press = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line));
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

    @Test
    void hourlyBucketsFollowThePlantZone() throws Exception {
        save(10, T0800, T0845);  // 14:15 local -> 14:00 local bucket
        save(20, T0845, T0930);  // ends exactly on 15:00 local -> previous bucket
        save(40, T0930, T0945);  // 15:15 local -> 15:00 local bucket

        mvc.perform(get("/api/machines/" + press.getId() + "/production-counts/hourly")
                .param("from", T0800.toString())
                .param("to", T1000.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.buckets.length()").value(3))
            .andExpect(jsonPath("$.buckets[0].bucketStart").value(T0730.toString()))
            .andExpect(jsonPath("$.buckets[0].goodCount").value(0))
            .andExpect(jsonPath("$.buckets[1].bucketStart").value(T0830.toString()))
            .andExpect(jsonPath("$.buckets[1].goodCount").value(30))
            .andExpect(jsonPath("$.buckets[2].bucketStart").value(T0930.toString()))
            .andExpect(jsonPath("$.buckets[2].goodCount").value(40));
    }

    private void save(long good, Instant start, Instant end) {
        countRepository.save(new ProductionCount(press, UUID.randomUUID(), good, 0, start, end, sequence.incrementAndGet()));
    }
}
