package io.github.juanjguerrer.meslite.line.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLineRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String code,
    String description
) { }
