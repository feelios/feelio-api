package com.korit.feelioapi.domain.goal.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * goals 테이블 행 매핑 (순수 POJO — JPA 아님).
 * current_amount(기본 0)·status(기본 ACTIVE)는 서버 관리 컬럼.
 */
@Getter
@Setter
public class Goal {

    private Long goalId;
    private Long userId;
    private String name;
    private Integer targetAmount;
    private Integer currentAmount;
    private Long initialAmount;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Boolean isMain;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
