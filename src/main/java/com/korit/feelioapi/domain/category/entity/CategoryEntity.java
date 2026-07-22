package com.korit.feelioapi.domain.category.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryEntity {
    private Long categoryId;
    private Long userId;
    private String name;
    private String type;
    
    public CategoryEntity(Long userId, String name, String type) {
        this.userId = userId;
        this.name = name;
        this.type = type;
    }
}
