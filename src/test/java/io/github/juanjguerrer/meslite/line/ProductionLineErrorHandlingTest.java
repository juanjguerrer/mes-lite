package io.github.juanjguerrer.meslite.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.machine.Machine;
import io.github.juanjguerrer.meslite.machine.MachineRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductionLineErrorHandlingTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductionLineRepository lineRepository;

    @Autowired
    private MachineRepository machineRepository;

    @BeforeEach
    void cleanDatabase() {
        machineRepository.deleteAll();
        lineRepository.deleteAll();
    }

    @Test
    void returns404ForUnknownLine() throws Exception {
        mvc.perform(get("/api/lines/999999"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.detail").value("Production line with id 999999 not found"))
            .andExpect(jsonPath("$.instance").value("/api/lines/999999"));
    }

    @Test
    void returns404WhenUpdatingUnknownLine() throws Exception {
        mvc.perform(put("/api/lines/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Assembly 1"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns404WhenDeletingUnknownLine() throws Exception {
        mvc.perform(delete("/api/lines/999999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns409ForDuplicateCodeAfterNormalization() throws Exception {
        lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));

        mvc.perform(post("/api/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Assembly 2", "code": " line-01 "}
                    """))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("A production line with the same code already exists."))
            .andExpect(jsonPath("$.detail", not(containsString("insert"))));
    }

    @Test
    void returns409WhenDeletingLineWithMachines() throws Exception {
        ProductionLine line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        machineRepository.save(new Machine("Press 3", "PRS-003", null, 12000, line));

        mvc.perform(delete("/api/lines/" + line.getId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail", not(containsString("production_lines"))))
            .andExpect(jsonPath("$.detail", not(containsString("delete"))));

        assertThat(lineRepository.existsById(line.getId())).isTrue();
    }

    @Test
    void returns422ForInvalidCodeFormat() throws Exception {
        mvc.perform(post("/api/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Assembly 1", "code": "-LINE"}
                    """))
            .andExpect(status().isUnprocessableContent())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail", containsString("Code")));
    }

    @Test
    void returns400WithFieldErrorsForInvalidBody() throws Exception {
        mvc.perform(post("/api/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": " ", "code": ""}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Request validation failed"))
            .andExpect(jsonPath("$.errors.name").exists())
            .andExpect(jsonPath("$.errors.code").exists());
    }

    @Test
    void returns400ForMalformedJson() throws Exception {
        mvc.perform(post("/api/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Assembly 1\","))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void returns400ForUnknownSortProperty() throws Exception {
        mvc.perform(get("/api/lines").param("sort", "unknown"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Unknown sort property: unknown"));
    }
}
