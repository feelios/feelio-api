package com.korit.feelioapi.domain.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CustomCategoryCreateRequest(
        @NotBlank String name,
        @NotNull @Pattern(regexp = "^(EXPENSE|INCOME)$") String type
) {}
