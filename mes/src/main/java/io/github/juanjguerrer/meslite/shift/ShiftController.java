package io.github.juanjguerrer.meslite.shift;

import java.net.URI;
import java.util.List;

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

import io.github.juanjguerrer.meslite.common.LogicalOperator;
import io.github.juanjguerrer.meslite.shift.dto.CreateShiftRequest;
import io.github.juanjguerrer.meslite.shift.dto.ShiftResponse;
import io.github.juanjguerrer.meslite.shift.dto.UpdateShiftRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Shifts", description = "Work shifts of a production line, defined in plant local time")
@RequestMapping("/api")
public class ShiftController {
    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PostMapping("/lines/{lineId}/shifts")
    @Operation(summary = "Create a shift in a production line (times must not overlap other shifts)")
    public ResponseEntity<ShiftResponse> create(
        @PathVariable Long lineId,
        @Valid @RequestBody CreateShiftRequest request
    ) {
        ShiftResponse response = shiftService.create(request, lineId);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/shifts/{id}")
            .buildAndExpand(response.id())
            .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/shifts/{id}")
    @Operation(summary = "Get a shift by id")
    public ShiftResponse findById(@PathVariable Long id) {
        return shiftService.findById(id);
    }

    @GetMapping("/shifts")
    @Operation(summary = "List shifts across all lines with optional filters")
    public Page<ShiftResponse> list(
        @RequestParam(defaultValue = "AND") LogicalOperator operator,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String name,
        @ParameterObject Pageable pageable
    ) {
        return shiftService.list(pageable, code, name, operator);
    }

    @GetMapping("/lines/{lineId}/shifts")
    @Operation(summary = "List the shifts of a production line")
    public List<ShiftResponse> listByLine(
        @PathVariable Long lineId
    ) {
        return shiftService.listByLine(lineId);
    }

    @PutMapping("/shifts/{id}")
    @Operation(summary = "Update the name and times of a shift (its code cannot change)")
    public ShiftResponse update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateShiftRequest request
    ) {
        return shiftService.update(id, request);
    }

    @DeleteMapping("/shifts/{id}")
    @Operation(summary = "Delete a shift")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        shiftService.delete(id);
    }
}
