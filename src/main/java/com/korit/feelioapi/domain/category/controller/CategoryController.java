package com.korit.feelioapi.domain.category.controller;

import com.korit.feelioapi.domain.category.dto.*;
import com.korit.feelioapi.domain.category.service.CategoryService;
import com.korit.feelioapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<CategoryListResponse> getCategories(
            @AuthenticationPrincipal Long userId,
            @RequestParam String type
    ) {
        return ApiResponse.success(categoryService.getCategories(userId, type));
    }

    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryDto> createCustomCategory(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CustomCategoryCreateRequest request
    ) {
        return ApiResponse.success(categoryService.createCustomCategory(userId, request));
    }

    @DeleteMapping("/custom/{customCategoryId}")
    public ApiResponse<Map<String, Boolean>> deleteCustomCategory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long customCategoryId
    ) {
        return ApiResponse.success(categoryService.deleteCustomCategory(userId, customCategoryId));
    }

    @PutMapping("/order")
    public ApiResponse<Map<String, Boolean>> updateCategoryOrders(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid CategoryOrderUpdateRequest request
    ) {
        return ApiResponse.success(categoryService.updateCategoryOrders(userId, request));
    }
}
