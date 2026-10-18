package io.github.juanjguerrer.meslite.line.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLineRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 20000) String description
) { }
