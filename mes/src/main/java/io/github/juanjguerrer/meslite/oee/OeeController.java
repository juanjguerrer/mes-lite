package io.github.juanjguerrer.meslite.oee;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.juanjguerrer.meslite.oee.dto.OeeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "OEE", description = "Overall Equipment Effectiveness of machines and production lines")
@RequestMapping("/api")
public class OeeController {
    private final OeeService service;

    public OeeController(OeeService service) {
        this.service = service;
    }

    @GetMapping("/machines/{id}/oee")
    @Operation(
        summary = "Get the OEE of a machine",
        description = "Pass either from/to (ISO instants) or shiftCode/date (plant local date), not both. "
            + "Availability = RUNNING / planned time (range minus PLANNED_STOP and unknown time); "
            + "performance = total pieces x ideal cycle / RUNNING; quality = good / total. "
            + "Factors are ratios rounded to 4 decimals; null when they cannot be computed (see warnings)."
    )
    public OeeResponse getMachineOee(
        @PathVariable Long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String shiftCode,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.machineOee(id, from, to, shiftCode, date);
    }

    @GetMapping("/lines/{id}/oee")
    @Operation(
        summary = "Get the OEE of a production line",
        description = "The OEE of the line's reference machine (usually its bottleneck or last machine). "
            + "Same parameters as the machine OEE; 422 if the line has no reference machine."
    )
    public OeeResponse getLineOee(
        @PathVariable Long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(required = false) String shiftCode,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.machineOeeFromLine(id, from, to, shiftCode, date);
    }
}
