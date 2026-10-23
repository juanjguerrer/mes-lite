package io.github.juanjguerrer.meslite.machine;

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
import io.github.juanjguerrer.meslite.machine.dto.CreateMachineRequest;
import io.github.juanjguerrer.meslite.machine.dto.MachineResponse;
import io.github.juanjguerrer.meslite.machine.dto.UpdateMachineRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;


@RestController
@Tag(name = "Machines", description = "Machines that belong to a production line")
@RequestMapping("/api")
public class MachineController {
    private final MachineService service;

    public MachineController(MachineService service) {
        this.service = service;
    }

    @PostMapping("/lines/{lineId}/machines")
    @Operation(summary = "Create a machine in a production line")
    public ResponseEntity<MachineResponse> create(
        @PathVariable Long lineId,
        @Valid @RequestBody CreateMachineRequest request
    ) {
        MachineResponse response = service.create(request, lineId);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/machines/{id}")
            .buildAndExpand(response.id())
            .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/machines/{id}")
    @Operation(summary = "Get a machine by id")
    public MachineResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/machines")
    @Operation(summary = "List machines across all lines with optional filters")
    public Page<MachineResponse> list(
        @RequestParam(defaultValue = "AND") LogicalOperator operator,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String name,
        @ParameterObject Pageable pageable
    ) {
        return service.list(pageable, code, name, operator);
    }

    @GetMapping("/lines/{lineId}/machines")
    @Operation(summary = "List the machines of a production line")
    public Page<MachineResponse> listByLine(
        @PathVariable Long lineId,
        @ParameterObject Pageable pageable
    ) {
        return service.listByLine(pageable, lineId);
    }

    @PutMapping("/machines/{id}")
    @Operation(summary = "Update a machine (its code cannot change)")
    public MachineResponse update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateMachineRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/machines/{id}")
    @Operation(summary = "Delete a machine")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
