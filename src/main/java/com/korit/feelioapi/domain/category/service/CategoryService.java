package com.korit.feelioapi.domain.category.service;

import com.korit.feelioapi.domain.category.dto.*;
import com.korit.feelioapi.domain.category.entity.CategoryEntity;
import com.korit.feelioapi.domain.category.mapper.CategoryMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryListResponse getCategories(Long userId, String type) {
        ensureCategoryOrdersInitialized(userId, type);
        return new CategoryListResponse(categoryMapper.findCategoriesWithOrder(userId, type));
    }

    @Transactional
    public CategoryDto createCustomCategory(Long userId, CustomCategoryCreateRequest request) {
        ensureCategoryOrdersInitialized(userId, request.type());

        CategoryEntity entity = new CategoryEntity(userId, request.name(), request.type());
        categoryMapper.insertCustomCategory(entity);
        Long newId = entity.getCategoryId();

        categoryMapper.insertCategoryOrder(userId, newId, request.type());
        
        return categoryMapper.findCategoriesWithOrder(userId, request.type()).stream()
                .filter(c -> Boolean.TRUE.equals(c.isCustom()) && c.categoryId().equals(newId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    @Transactional
    public Map<String, Boolean> deleteCustomCategory(Long userId, Long customCategoryId) {
        // deleteCategoryOrder first
        categoryMapper.deleteCategoryOrder(userId, customCategoryId);
        // Soft Delete (updates is_active = 0)
        categoryMapper.deleteCustomCategory(customCategoryId, userId);

        return Map.of("deleted", true);
    }

    @Transactional
    public Map<String, Boolean> updateCategoryOrders(Long userId, CategoryOrderUpdateRequest request) {
        if (!request.orders().isEmpty()) {
            categoryMapper.upsertCategoryOrders(userId, request.type(), request.orders());
        }
        return Map.of("updated", true);
    }

    private void ensureCategoryOrdersInitialized(Long userId, String type) {
        int orderCount = categoryMapper.countCategoryOrders(userId, type);
        if (orderCount == 0) {
            categoryMapper.initializeCategoryOrders(userId, type);
        }
    }
}
