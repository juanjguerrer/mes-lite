package io.github.juanjguerrer.meslite.production;

import java.net.URI;
import java.time.Instant;

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

import io.github.juanjguerrer.meslite.production.dto.CreateProductionCountRequest;
import io.github.juanjguerrer.meslite.production.dto.ProductionCountResponse;
import io.github.juanjguerrer.meslite.production.dto.ProductionTotalsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Production counts", description = "Good and scrap pieces reported by machines per time interval")
@RequestMapping("/api")
public class ProductionCountController {
    private final ProductionCountService service;

    public ProductionCountController(ProductionCountService service) {
        this.service = service;
    }

    @PostMapping("/lines/{lineCode}/machines/{machineCode}/counts")
    @Operation(summary = "Report good and scrap pieces for an interval (idempotent by eventId)")
    public ResponseEntity<ProductionCountResponse> ingest(
        @PathVariable String lineCode,
        @PathVariable String machineCode,
        @Valid @RequestBody CreateProductionCountRequest request
    ) {
        var result = service.ingest(request, lineCode, machineCode);
        if (result.created()) {
            URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/api/production-counts/{id}")
            .buildAndExpand(result.count().id())
            .toUri();
            return ResponseEntity.created(location).body(result.count());
        }
        return ResponseEntity.ok(result.count());
    }

    @GetMapping("/machines/{machineId}/production-counts/totals")
    @Operation(
        summary = "Get good and scrap totals of a machine within a range",
        description = "A count belongs to the range when from < intervalEnd <= to, so no count is counted twice "
            + "across adjacent ranges. 'to' is clipped to the current time and the range may not exceed 31 days."
    )
    public ProductionTotalsResponse totals(
        @PathVariable Long machineId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return service.totals(machineId, from, to);
    }

    @GetMapping("/production-counts/{id}")
    @Operation(summary = "Get a production count by id")
    public ProductionCountResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
