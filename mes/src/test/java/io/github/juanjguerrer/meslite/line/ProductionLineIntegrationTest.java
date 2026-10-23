package io.github.juanjguerrer.meslite.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.github.juanjguerrer.meslite.TestcontainersConfiguration;
import io.github.juanjguerrer.meslite.machine.MachineRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductionLineIntegrationTest {

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
    void createsAndReadsLineWithNormalizedCode() throws Exception {
        String location = mvc.perform(post("/api/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Assembly 1", "code": " line-01 "}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.code").value("LINE-01"))
            .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Assembly 1"))
            .andExpect(jsonPath("$.code").value("LINE-01"))
            .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void updatesNameAndDescriptionAndRefreshesUpdatedAt() throws Exception {
        ProductionLine line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        Instant before = line.getUpdatedAt();

        String body = mvc.perform(put("/api/lines/" + line.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Assembly A", "description": "Renamed"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Assembly A"))
            .andExpect(jsonPath("$.description").value("Renamed"))
            .andExpect(jsonPath("$.code").value("LINE-01"))
            .andReturn().getResponse().getContentAsString();

        ProductionLine reloaded = lineRepository.findById(line.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Assembly A");
        assertThat(reloaded.getUpdatedAt()).isAfter(before);
        assertThat(body).contains(reloaded.getUpdatedAt().toString().substring(0, 19));
    }

    @Test
    void deletesLine() throws Exception {
        ProductionLine line = lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));

        mvc.perform(delete("/api/lines/" + line.getId()))
            .andExpect(status().isNoContent());

        assertThat(lineRepository.existsById(line.getId())).isFalse();
    }

    @Test
    void filtersByCodeContainsIgnoringCase() throws Exception {
        seedLines();

        mvc.perform(get("/api/lines").param("code", "line"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("LINE-01", "LINE-02")));
    }

    @Test
    void filtersByNameContainsIgnoringCase() throws Exception {
        seedLines();

        mvc.perform(get("/api/lines").param("name", "ASSEMBLY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("LINE-01", "LINE-02")));
    }

    @Test
    void combinesFiltersWithAndByDefault() throws Exception {
        seedLines();

        mvc.perform(get("/api/lines").param("code", "line").param("name", "paint"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void combinesFiltersWithOr() throws Exception {
        seedLines();

        mvc.perform(get("/api/lines").param("code", "line").param("name", "paint").param("operator", "OR"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void treatsLikeWildcardsAsLiterals() throws Exception {
        seedLines();

        mvc.perform(get("/api/lines").param("name", "_"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].code", containsInAnyOrder("PNT_01")));

        mvc.perform(get("/api/lines").param("name", "%"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void ignoresBlankFilters() throws Exception {
        seedLines();

        mvc.perform(get("/api/lines").param("code", "").param("name", " "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void paginatesAndSorts() throws Exception {
        seedLines();

        mvc.perform(get("/api/lines").param("size", "2").param("page", "1").param("sort", "code,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].code").value("PNT_01"))
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void capsPageSizeAtConfiguredMaximum() throws Exception {
        mvc.perform(get("/api/lines").param("size", "1000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page.size").value(100));
    }

    private void seedLines() {
        lineRepository.save(new ProductionLine("Assembly 1", "LINE-01", null));
        lineRepository.save(new ProductionLine("Assembly 2", "LINE-02", null));
        lineRepository.save(new ProductionLine("Paint_shop", "PNT_01", null));
    }
}
