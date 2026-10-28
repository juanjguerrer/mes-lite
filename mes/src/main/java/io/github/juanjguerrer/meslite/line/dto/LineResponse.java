package io.github.juanjguerrer.meslite.line.dto;

import java.time.Instant;

import io.github.juanjguerrer.meslite.line.ProductionLine;
import io.swagger.v3.oas.annotations.media.Schema;

public record LineResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long referenceMachineId
) {
    public static LineResponse from(ProductionLine productionLine) {
        return new LineResponse(
            productionLine.getId(),
            productionLine.getName(),
            productionLine.getCode(),
            productionLine.getDescription(),
            productionLine.getCreatedAt(),
            productionLine.getUpdatedAt(),
            productionLine.getReferenceMachine() == null ? null : productionLine.getReferenceMachine().getId()
        );
    }
}
