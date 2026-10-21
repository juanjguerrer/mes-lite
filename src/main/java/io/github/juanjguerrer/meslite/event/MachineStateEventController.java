package io.github.juanjguerrer.meslite.event;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.juanjguerrer.meslite.event.dto.CreateStateEventRequest;
import io.github.juanjguerrer.meslite.event.dto.MachineCurrentStateResponse;
import io.github.juanjguerrer.meslite.event.dto.StateEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Machine state events", description = "State changes reported by machines on the shop floor")
@RequestMapping("/api")
public class MachineStateEventController {
    private final MachineStateEventService service;

    public MachineStateEventController(MachineStateEventService service) {
        this.service = service;
    }

    @PostMapping("/lines/{lineCode}/machines/{machineCode}/events")
    @Operation(summary = "Report a machine state change (idempotent by eventId)")
    public ResponseEntity<StateEventResponse> ingest(
        @PathVariable String lineCode,
        @PathVariable String machineCode,
        @Valid @RequestBody CreateStateEventRequest request
    ) {
        var result = service.ingest(lineCode, machineCode, request);
        if (result.created()) {
            URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/events/{id}")
            .buildAndExpand(result.event().id())
            .toUri();
            return ResponseEntity.created(location).body(result.event());
        }
        return ResponseEntity.ok(result.event());
    }

    @GetMapping("/events/{id}")
    @Operation(summary = "Get a machine state event by id")
    public StateEventResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/machines/{id}/events")
    @Operation(summary = "Get the history of machine state events for a given machine")
    public Page<StateEventResponse> history(
        @PathVariable Long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @ParameterObject @PageableDefault(sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return service.history(id, from, to, pageable);
    }

    @GetMapping("/machines/{id}/state")
    @Operation(summary = "Get the current state of a machine")
    public MachineCurrentStateResponse currentState(@PathVariable Long id) {
        return service.currentState(id);
    }

    @GetMapping("/lines/{id}/machines/states")
    @Operation(summary = "Get the current state of all machines in a production line")
    public List<MachineCurrentStateResponse> currentStatesByLine(@PathVariable Long id) {
        return service.currentStatesByLine(id);
    }
}
