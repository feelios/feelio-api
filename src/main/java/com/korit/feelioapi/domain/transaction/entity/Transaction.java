package com.korit.feelioapi.domain.transaction.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class Transaction {
    private Long transactionId;
    private Long userId;
    private Long emotionId;
    private Long categoryId;
    private String type;
    private Integer amount;
    private String memo;
    private LocalDateTime occurredAt;
    private Long goalId;
    private boolean isSettled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
