package com.korit.feelioapi.domain.category.mapper;

import com.korit.feelioapi.domain.category.dto.CategoryDto;
import com.korit.feelioapi.domain.category.dto.CategoryOrderDto;
import com.korit.feelioapi.domain.category.entity.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CategoryMapper {
    void initializeCategoryOrders(@Param("userId") Long userId, @Param("type") String type);
    
    List<CategoryDto> findCategoriesWithOrder(@Param("userId") Long userId, @Param("type") String type);
    
    void insertCustomCategory(CategoryEntity entity);
    
    void insertCategoryOrder(@Param("userId") Long userId, @Param("categoryId") Long categoryId, @Param("type") String type);
    
    // Deprecated but kept for compilation compatibility if needed somewhere
    Map<String, Object> findCustomCategoryById(@Param("customCategoryId") Long customCategoryId, @Param("userId") Long userId);
    
    void deleteCustomCategory(@Param("customCategoryId") Long customCategoryId, @Param("userId") Long userId);
    
    void deleteCategoryOrder(@Param("userId") Long userId, @Param("categoryId") Long categoryId);
    
    void upsertCategoryOrders(@Param("userId") Long userId, @Param("type") String type, @Param("orders") List<CategoryOrderDto> orders);
}
