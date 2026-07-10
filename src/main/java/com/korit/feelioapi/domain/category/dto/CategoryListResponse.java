package com.korit.feelioapi.domain.category.dto;

import java.util.List;

public record CategoryListResponse(
        List<CategoryDto> categories
) {}
