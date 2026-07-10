package com.korit.feelioapi.domain.category.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record CategoryOrderUpdateRequest(
        @NotNull @Pattern(regexp = "^(EXPENSE|INCOME)$") String type,
        @NotNull @Valid List<CategoryOrderDto> orders
) {}
