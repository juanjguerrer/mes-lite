package io.github.juanjguerrer.meslite.production.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateProductionCountRequest(
    @NotNull UUID eventId,
    @NotNull @PositiveOrZero Long goodCount,
    @NotNull @PositiveOrZero Long scrapCount,
    @NotNull Instant intervalStart,
    @NotNull Instant intervalEnd
) { }
