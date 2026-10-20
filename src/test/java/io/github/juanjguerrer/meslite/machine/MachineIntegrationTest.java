package io.github.juanjguerrer.meslite.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MachineIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductionLineRepository lineRepository;

    @Autowired
    private MachineRepository machineRepository;

    private ProductionLine line1;
    private ProductionLine line2;

    @BeforeEach
    void setUp() {
        machineRepository.deleteAll();
        lineRepository.deleteAll();
        line1 = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        line2 = lineRepository.save(new ProductionLine("Assembly 2", "LINE-02", null));
    }

    // --- create ---

    @Test
    void createsMachineConvertingCycleTimeToMillis() throws Exception {
        Long id = Long.valueOf(createMachine(line1.getId(), """
                {"name": "Press 3", "code": " prs-003 ", "idealCycleTime": 12, "idealCycleTimeUnit": "SECONDS"}
                """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("PRS-003"))
            .andExpect(jsonPath("$.idealCycleTimeMs").value(12000))
            .andExpect(jsonPath("$.lineId").value(line1.getId()))
            .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(get("/api/machines/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Press 3"));
    }

    @Test
    void createReturnsLocationOfMachineResource() throws Exception {
        createMachine(line1.getId(), """
                {"name": "Press 3", "code": "PRS-003"}
                """)
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", endsWith("/api/machines/" + onlyMachine().getId())));
    }

    @Test
    void createsMachineWithoutCycleTime() throws Exception {
        createMachine(line1.getId(), """
                {"name": "Manual station", "code": "MAN-001"}
                """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idealCycleTimeMs").doesNotExist());
    }

    @Test
    void allowsSameCodeOnDifferentLines() throws Exception {
        createMachine(line1.getId(), """
                {"name": "Press 3", "code": "PRS-003"}
                """).andExpect(status().isCreated());

        createMachine(line2.getId(), """
                {"name": "Press 3", "code": "PRS-003"}
                """).andExpect(status().isCreated());
    }

    @Test
    void returns409ForDuplicateCodeOnSameLine() throws Exception {
        createMachine(line1.getId(), """
                {"name": "Press 3", "code": "PRS-003"}
                """).andExpect(status().isCreated());

        createMachine(line1.getId(), """
                {"name": "Press 3 bis", "code": "prs-003"}
                """)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("A machine with the same code already exists in this production line."));
    }

    @Test
    void returns404WhenCreatingInUnknownLine() throws Exception {
        createMachine(999999L, """
                {"name": "Press 3", "code": "PRS-003"}
                """)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Production line with id 999999 not found"));
    }

    @Test
    void returns422ForCycleTimeWithoutUnit() throws Exception {
        createMachine(line1.getId(), """
                {"name": "Press 3", "code": "PRS-003", "idealCycleTime": 12}
                """)
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns422ForNonPositiveCycleTime() throws Exception {
        createMachine(line1.getId(), """
                {"name": "Press 3", "code": "PRS-003", "idealCycleTime": 0, "idealCycleTimeUnit": "SECONDS"}
                """)
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns422ForCycleTimeOver24Hours() throws Exception {
        createMachine(line1.getId(), """
                {"name": "Oven", "code": "OVN-001", "idealCycleTime": 25, "idealCycleTimeUnit": "HOURS"}
                """)
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns422ForInvalidCode() throws Exception {
        createMachine(line1.getId(), """
                {"name": "Press 3", "code": "PRS 003"}
                """)
            .andExpect(status().isUnprocessableContent());
    }

    @Test
    void returns400ForUnknownCycleTimeUnit() throws Exception {
        createMachine(line1.getId(), """
                {"name": "Press 3", "code": "PRS-003", "idealCycleTime": 12, "idealCycleTimeUnit": "SECS"}
                """)
            .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WithFieldErrorsForInvalidBody() throws Exception {
        createMachine(line1.getId(), """
                {"name": "", "code": " "}
                """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").exists())
            .andExpect(jsonPath("$.errors.code").exists());
    }

    // --- read ---

    @Test
    void returns404ForUnknownMachine() throws Exception {
        mvc.perform(get("/api/machines/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Machine with id 999999 not found"));
    }

    @Test
    void listsOnlyMachinesOfTheGivenLine() throws Exception {
        machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line1));
        machineRepository.save(new Machine("Labeler", "LBL-001", null, 300, line1));
        machineRepository.save(new Machine("Filler", "FIL-001", null, 100, line2));

        mvc.perform(get("/api/lines/" + line1.getId() + "/machines"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("PRS-003", "LBL-001")));
    }

    @Test
    void returns404WhenListingMachinesOfUnknownLine() throws Exception {
        mvc.perform(get("/api/lines/999999/machines"))
            .andExpect(status().isNotFound());
    }

    @Test
    void filtersMachinesByCodeAndName() throws Exception {
        machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line1));
        machineRepository.save(new Machine("Press 4", "PRS-004", null, 12000, line2));
        machineRepository.save(new Machine("Labeler", "LBL-001", null, 300, line1));

        mvc.perform(get("/api/machines").param("code", "prs"))
            .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("PRS-003", "PRS-004")));

        mvc.perform(get("/api/machines").param("name", "LABEL"))
            .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("LBL-001")));

        mvc.perform(get("/api/machines").param("code", "prs-003").param("name", "labeler").param("operator", "OR"))
            .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("PRS-003", "LBL-001")));
    }

    // --- update ---

    @Test
    void updatesMachineButKeepsCode() throws Exception {
        Machine machine = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line1));

        mvc.perform(put("/api/machines/" + machine.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Press 3 (rebuilt)", "description": "New tooling", "idealCycleTime": 5, "idealCycleTimeUnit": "MINUTES"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Press 3 (rebuilt)"))
            .andExpect(jsonPath("$.code").value("PRS-003"))
            .andExpect(jsonPath("$.idealCycleTimeMs").value(300000));

        assertThat(machineRepository.findById(machine.getId()).orElseThrow().getIdealCycleTimeMs()).isEqualTo(300000);
    }

    @Test
    void returns404WhenUpdatingUnknownMachine() throws Exception {
        mvc.perform(put("/api/machines/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Press 3"}
                    """))
            .andExpect(status().isNotFound());
    }

    // --- delete ---

    @Test
    void deletesMachine() throws Exception {
        Machine machine = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line1));

        mvc.perform(delete("/api/machines/" + machine.getId()))
            .andExpect(status().isNoContent());

        assertThat(machineRepository.existsById(machine.getId())).isFalse();
    }

    @Test
    void returns404WhenDeletingUnknownMachine() throws Exception {
        mvc.perform(delete("/api/machines/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void lineCanBeDeletedOnceItsMachinesAreGone() throws Exception {
        Machine machine = machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line1));

        mvc.perform(delete("/api/lines/" + line1.getId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("The line cannot be deleted because it has associated machines."));

        mvc.perform(delete("/api/machines/" + machine.getId())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/lines/" + line1.getId())).andExpect(status().isNoContent());
    }

    private ResultActions createMachine(Long lineId, String body) throws Exception {
        return mvc.perform(post("/api/lines/" + lineId + "/machines")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private Machine onlyMachine() {
        return machineRepository.findAll().getFirst();
    }
}
