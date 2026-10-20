package io.github.juanjguerrer.meslite.line;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.juanjguerrer.meslite.common.LogicalOperator;
import io.github.juanjguerrer.meslite.line.dto.CreateLineRequest;
import io.github.juanjguerrer.meslite.line.dto.LineResponse;
import io.github.juanjguerrer.meslite.line.dto.UpdateLineRequest;

@WebMvcTest(ProductionLineController.class)
class ProductionLineControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final LineResponse LINE = new LineResponse(1L, "Assembly 1", "LINE-01", null, NOW, NOW);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductionLineService service;

    @Test
    void createReturns201WithLocation() throws Exception {
        when(service.create(any(CreateLineRequest.class))).thenReturn(LINE);

        mvc.perform(post("/api/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Assembly 1", "code": "line-01"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost/api/lines/1"))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.code").value("LINE-01"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        mvc.perform(post("/api/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": " ", "code": "LINE-01"}
                    """))
            .andExpect(status().isBadRequest());

        verify(service, never()).create(any());
    }

    @Test
    void createRejectsMissingCode() throws Exception {
        mvc.perform(post("/api/lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Assembly 1"}
                    """))
            .andExpect(status().isBadRequest());

        verify(service, never()).create(any());
    }

    @Test
    void findByIdReturnsLine() throws Exception {
        when(service.findById(1L)).thenReturn(LINE);

        mvc.perform(get("/api/lines/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Assembly 1"));
    }

    @Test
    void listPassesFiltersAndPagingToService() throws Exception {
        Pageable pageable = PageRequest.of(0, 5);
        when(service.list(any(Pageable.class), eq("line"), eq("assembly"), eq(LogicalOperator.OR)))
            .thenReturn(new PageImpl<>(List.of(LINE), pageable, 1));

        mvc.perform(get("/api/lines")
                .param("code", "line")
                .param("name", "assembly")
                .param("operator", "OR")
                .param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].code").value("LINE-01"))
            .andExpect(jsonPath("$.page.size").value(5))
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listDefaultsToAndWithoutFilters() throws Exception {
        when(service.list(any(Pageable.class), isNull(), isNull(), eq(LogicalOperator.AND)))
            .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/lines"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void updateReturnsUpdatedLine() throws Exception {
        LineResponse updated = new LineResponse(1L, "Assembly A", "LINE-01", "Renamed", NOW, NOW);
        when(service.update(eq(1L), any(UpdateLineRequest.class))).thenReturn(updated);

        mvc.perform(put("/api/lines/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Assembly A", "description": "Renamed"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Assembly A"));
    }

    @Test
    void updateRejectsBlankName() throws Exception {
        mvc.perform(put("/api/lines/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": ""}
                    """))
            .andExpect(status().isBadRequest());

        verify(service, never()).update(any(), any());
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
        when(service.findById(1L)).thenThrow(new IllegalStateException("SELECT * FROM production_lines failed"));

        mvc.perform(get("/api/lines/1"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/lines/1"))
            .andExpect(status().isNoContent());

        verify(service).delete(1L);
    }
}
