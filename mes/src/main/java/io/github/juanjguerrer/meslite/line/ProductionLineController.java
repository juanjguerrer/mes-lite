package io.github.juanjguerrer.meslite.line;

import java.net.URI;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.juanjguerrer.meslite.common.query.LogicalOperator;
import io.github.juanjguerrer.meslite.line.dto.CreateLineRequest;
import io.github.juanjguerrer.meslite.line.dto.LineResponse;
import io.github.juanjguerrer.meslite.line.dto.UpdateLineRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Production lines", description = "Catalog of production lines")
@RequestMapping("/api/lines")
public class ProductionLineController {
    private final ProductionLineService service;

    public ProductionLineController(ProductionLineService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a production line")
    public ResponseEntity<LineResponse> create(@Valid @RequestBody CreateLineRequest request) {
        LineResponse response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a production line by id")
    public LineResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    @Operation(summary = "List production lines with optional filters")
    public Page<LineResponse> list(
        @RequestParam(defaultValue = "AND") LogicalOperator operator,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String name,
        @ParameterObject Pageable pageable
    ) {
        return service.list(pageable, code, name, operator);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update the name and description of a production line")
    public LineResponse update(@PathVariable Long id, @Valid @RequestBody UpdateLineRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a production line without machines")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
