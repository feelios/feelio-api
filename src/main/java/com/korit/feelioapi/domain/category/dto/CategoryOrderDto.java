package com.korit.feelioapi.domain.category.dto;

import jakarta.validation.constraints.NotNull;

public record CategoryOrderDto(
        @NotNull Long categoryId,
        @NotNull Integer sortOrder
) {}
