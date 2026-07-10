package com.korit.feelioapi.domain.category.dto;

public record CategoryDto(
        Long categoryId,
        String name,
        String type,
        Boolean isCustom,
        Integer sortOrder
) {}
