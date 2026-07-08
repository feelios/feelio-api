package com.korit.feelioapi.domain.meta.dto;

import com.korit.feelioapi.domain.meta.entity.Category;

/**
 * 카테고리 마스터 응답 (API-CONTRACT §5). type: EXPENSE | INCOME. is_active 는 노출하지 않는다.
 */
public record CategoryResponse(
        Long categoryId,
        String name,
        String type,
        int sortOrder
) {
    public static CategoryResponse of(Category category) {
        return new CategoryResponse(
                category.getCategoryId(),
                category.getName(),
                category.getType(),
                category.getSortOrder()
        );
    }
}
