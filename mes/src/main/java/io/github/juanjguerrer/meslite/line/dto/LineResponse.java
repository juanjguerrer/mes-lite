package io.github.juanjguerrer.meslite.line.dto;

import java.time.Instant;

import io.github.juanjguerrer.meslite.line.ProductionLine;

public record LineResponse(
    Long id,
    String name,
    String code,
    String description,
    Instant createdAt,
    Instant updatedAt,
    Long referenceMachineId
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
