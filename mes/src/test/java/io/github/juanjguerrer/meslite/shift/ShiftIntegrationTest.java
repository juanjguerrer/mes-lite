package io.github.juanjguerrer.meslite.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;

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
import io.github.juanjguerrer.meslite.machine.MachineRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShiftIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private MachineStateEventRepository eventRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private ProductionLineRepository lineRepository;

    private ProductionLine line;
    private ProductionLine otherLine;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        otherLine = lineRepository.save(new ProductionLine("Bottling", "LINE-02", null));
    }

    // Other integration test classes delete lines; leftover shifts would block them (FK RESTRICT).
    @AfterEach
    void cleanDatabase() {
        shiftRepository.deleteAll();
        eventRepository.deleteAll();
        machineRepository.deleteAll();
        lineRepository.deleteAll();
    }

    // --- create ---

    @Test
    void createsDayShiftWithLocationAndNormalizedCode() throws Exception {
        String location = createShift(line.getId(), " a ", "Morning", "06:00", "14:00")
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", matchesPattern(".*/api/shifts/\\d+")))
            .andExpect(jsonPath("$.code").value("A"))
            .andExpect(jsonPath("$.lineId").value(line.getId()))
            .andExpect(jsonPath("$.startTime").value("06:00:00"))
            .andExpect(jsonPath("$.endTime").value("14:00:00"))
            .andExpect(jsonPath("$.crossesMidnight").value(false))
            .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Morning"));
    }

    @Test
    void nightShiftCrossesMidnight() throws Exception {
        createShift(line.getId(), "C", "Night", "22:00", "06:00")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.crossesMidnight").value(true));
    }

    @Test
    void threeShiftsThatOnlyTouchCanCoexist() throws Exception {
        createShift(line.getId(), "A", "Morning", "06:00", "14:00").andExpect(status().isCreated());
        createShift(line.getId(), "B", "Afternoon", "14:00", "22:00").andExpect(status().isCreated());
        createShift(line.getId(), "C", "Night", "22:00", "06:00").andExpect(status().isCreated());

        assertThat(shiftRepository.count()).isEqualTo(3);
    }

    @Test
    void returns422WhenStartEqualsEnd() throws Exception {
        createShift(line.getId(), "A", "Morning", "06:00", "06:00")
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns422WhenOverlappingAnotherShift() throws Exception {
        createShift(line.getId(), "A", "Morning", "06:00", "14:00").andExpect(status().isCreated());

        createShift(line.getId(), "B", "Afternoon", "13:00", "21:00")
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.detail", containsString("A")));
    }

    @Test
    void returns422WhenOverlappingANightShiftAfterMidnight() throws Exception {
        createShift(line.getId(), "C", "Night", "22:00", "06:00").andExpect(status().isCreated());

        createShift(line.getId(), "A", "Early", "05:00", "13:00")
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void shiftsOfDifferentLinesDoNotOverlap() throws Exception {
        createShift(line.getId(), "A", "Morning", "06:00", "14:00").andExpect(status().isCreated());

        createShift(otherLine.getId(), "A", "Morning", "06:00", "14:00").andExpect(status().isCreated());
    }

    @Test
    void returns409ForDuplicateCodeOnSameLine() throws Exception {
        createShift(line.getId(), "A", "Morning", "06:00", "14:00").andExpect(status().isCreated());

        createShift(line.getId(), "a", "Afternoon", "14:00", "22:00")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("A shift with the same code already exists in this production line."));
    }

    @Test
    void returns400ForMissingFields() throws Exception {
        mvc.perform(post("/api/lines/" + line.getId() + "/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.code").exists())
            .andExpect(jsonPath("$.errors.name").exists())
            .andExpect(jsonPath("$.errors.startTime").exists())
            .andExpect(jsonPath("$.errors.endTime").exists());
    }

    @Test
    void returns404WhenCreatingInUnknownLine() throws Exception {
        createShift(999999L, "A", "Morning", "06:00", "14:00")
            .andExpect(status().isNotFound());
    }

    // --- read ---

    @Test
    void listsShiftsOfALineOrderedByStartTime() throws Exception {
        createShift(line.getId(), "C", "Night", "22:00", "06:00").andExpect(status().isCreated());
        createShift(line.getId(), "A", "Morning", "06:00", "14:00").andExpect(status().isCreated());
        createShift(line.getId(), "B", "Afternoon", "14:00", "22:00").andExpect(status().isCreated());
        createShift(otherLine.getId(), "X", "Other", "08:00", "16:00").andExpect(status().isCreated());

        mvc.perform(get("/api/lines/" + line.getId() + "/shifts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code", contains("A", "B", "C")));
    }

    @Test
    void returns404WhenListingShiftsOfUnknownLine() throws Exception {
        mvc.perform(get("/api/lines/999999/shifts"))
            .andExpect(status().isNotFound());
    }

    @Test
    void filtersShiftsAcrossLines() throws Exception {
        createShift(line.getId(), "A", "Morning", "06:00", "14:00").andExpect(status().isCreated());
        createShift(line.getId(), "N_1", "Night", "22:00", "06:00").andExpect(status().isCreated());
        createShift(otherLine.getId(), "A", "Morning", "06:00", "14:00").andExpect(status().isCreated());

        mvc.perform(get("/api/shifts").param("name", "MORN"))
            .andExpect(jsonPath("$.page.totalElements").value(2));

        mvc.perform(get("/api/shifts").param("code", "_"))
            .andExpect(jsonPath("$.content[*].code", contains("N_1")));
    }

    @Test
    void returns404ForUnknownShift() throws Exception {
        mvc.perform(get("/api/shifts/999999"))
            .andExpect(status().isNotFound());
    }

    // --- update ---

    @Test
    void updatesNameAndTimesButKeepsCode() throws Exception {
        Long id = shiftRepository.save(new Shift(line, "A", "Morning", time("06:00"), time("14:00"))).getId();

        updateShift(id, "Early morning", "05:00", "13:00")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Early morning"))
            .andExpect(jsonPath("$.code").value("A"))
            .andExpect(jsonPath("$.startTime").value("05:00:00"));
    }

    @Test
    void updateWithoutChangingTimesDoesNotCollideWithItself() throws Exception {
        Long id = shiftRepository.save(new Shift(line, "A", "Morning", time("06:00"), time("14:00"))).getId();

        updateShift(id, "Renamed", "06:00", "14:00")
            .andExpect(status().isOk());
    }

    @Test
    void returns422WhenUpdateWouldOverlapAnotherShift() throws Exception {
        Long id = shiftRepository.save(new Shift(line, "A", "Morning", time("06:00"), time("14:00"))).getId();
        shiftRepository.save(new Shift(line, "B", "Afternoon", time("14:00"), time("22:00")));

        updateShift(id, "Morning", "06:00", "15:00")
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns404WhenUpdatingUnknownShift() throws Exception {
        updateShift(999999L, "Morning", "06:00", "14:00")
            .andExpect(status().isNotFound());
    }

    // --- delete ---

    @Test
    void deletesShift() throws Exception {
        Long id = shiftRepository.save(new Shift(line, "A", "Morning", time("06:00"), time("14:00"))).getId();

        mvc.perform(delete("/api/shifts/" + id))
            .andExpect(status().isNoContent());

        assertThat(shiftRepository.existsById(id)).isFalse();
    }

    @Test
    void returns404WhenDeletingUnknownShift() throws Exception {
        mvc.perform(delete("/api/shifts/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void lineWithShiftsCannotBeDeleted() throws Exception {
        shiftRepository.save(new Shift(line, "A", "Morning", time("06:00"), time("14:00")));

        mvc.perform(delete("/api/lines/" + line.getId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("The production line cannot be deleted because it has associated shifts."));
    }

    private ResultActions createShift(Long lineId, String code, String name, String start, String end) throws Exception {
        return mvc.perform(post("/api/lines/" + lineId + "/shifts")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"code": "%s", "name": "%s", "startTime": "%s", "endTime": "%s"}
                """.formatted(code, name, start, end)));
    }

    private ResultActions updateShift(Long id, String name, String start, String end) throws Exception {
        return mvc.perform(put("/api/shifts/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "%s", "startTime": "%s", "endTime": "%s"}
                """.formatted(name, start, end)));
    }

    private static LocalTime time(String value) {
        return LocalTime.parse(value);
    }
}
