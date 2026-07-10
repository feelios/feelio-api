package com.korit.feelioapi.domain.category.service;

import com.korit.feelioapi.domain.category.dto.*;
import com.korit.feelioapi.domain.category.entity.CustomCategoryEntity;
import com.korit.feelioapi.domain.category.mapper.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void 통합_카테고리_목록을_조회한다_초기화_포함() {
        Long userId = 1L;
        String type = "EXPENSE";

        when(categoryMapper.countCategoryOrders(userId, type)).thenReturn(0);
        when(categoryMapper.findCategoriesWithOrder(userId, type)).thenReturn(List.of(
                new CategoryDto(1L, "식비", "EXPENSE", false, 1)
        ));

        CategoryListResponse response = categoryService.getCategories(userId, type);

        verify(categoryMapper).initializeCategoryOrders(userId, type);
        assertThat(response.categories()).hasSize(1);
    }
}
