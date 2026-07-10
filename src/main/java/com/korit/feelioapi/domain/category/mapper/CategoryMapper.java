package com.korit.feelioapi.domain.category.mapper;

import com.korit.feelioapi.domain.category.dto.CategoryDto;
import com.korit.feelioapi.domain.category.dto.CategoryOrderDto;
import com.korit.feelioapi.domain.category.entity.CustomCategoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {
    int countCategoryOrders(@Param("userId") Long userId, @Param("type") String type);
    
    void initializeCategoryOrders(@Param("userId") Long userId, @Param("type") String type);
    
    List<CategoryDto> findCategoriesWithOrder(@Param("userId") Long userId, @Param("type") String type);
    
    void insertCustomCategory(CustomCategoryEntity entity);
    
    void insertCategoryOrder(@Param("userId") Long userId, @Param("categoryId") Long categoryId, @Param("isCustom") boolean isCustom, @Param("type") String type);
    
    CustomCategoryEntity findCustomCategoryById(@Param("customCategoryId") Long customCategoryId);
    
    void deleteCustomCategory(@Param("customCategoryId") Long customCategoryId);
    
    void deleteCategoryOrder(@Param("userId") Long userId, @Param("categoryId") Long categoryId, @Param("isCustom") boolean isCustom);
    
    void upsertCategoryOrders(@Param("userId") Long userId, @Param("type") String type, @Param("orders") List<CategoryOrderDto> orders);
}
