package com.korit.feelioapi.domain.category.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CustomCategoryEntity {
    private Long customCategoryId;
    private Long userId;
    private String name;
    private String type;
    private LocalDateTime createdAt;

    public CustomCategoryEntity(Long userId, String name, String type) {
        this.userId = userId;
        this.name = name;
        this.type = type;
    }
}
