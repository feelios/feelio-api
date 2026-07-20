package com.korit.feelioapi.domain.meta.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * categories 테이블 행 매핑 (순수 POJO — JPA 아님).
 * type: EXPENSE | INCOME. sort_order 는 타입별 순번.
 */
@Getter
@Setter
public class Category {

    private Long categoryId;
    private String name;
    private String type;
    private boolean isFixed;
    private boolean isBudgetable;
    private int sortOrder;
    private boolean isActive;
}
