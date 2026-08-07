package com.korit.feelioapi.domain.transaction.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 거래 생성·수정 요청 (계약 §6).
 *
 * 형식 검증은 전부 여기서 끝낸다. DB 제약(varchar 길이·CHECK·FK)에 기대면
 * 위반이 SQL 예외로 올라와 500 이 되기 때문이다(#195).
 * 참조 ID(categoryId·emotionId·goalId)의 실재·소유 검증은 DB 조회가 필요해
 * {@link com.korit.feelioapi.domain.transaction.service.TransactionService} 가 맡는다.
 */
public record TransactionCreateRequest(
        @NotBlank(message = "거래 유형은 필수입니다.")
        @Pattern(regexp = "EXPENSE|INCOME", message = "거래 유형은 EXPENSE 또는 INCOME 이어야 합니다.")
        String type,

        // amount 컬럼이 int 라 상한을 두지 않으면 큰 값이 그대로 내려가 오버플로로 깨진다.
        @NotNull(message = "금액은 필수입니다.")
        @Positive(message = "금액은 0보다 커야 합니다.")
        @Max(value = 2_000_000_000, message = "금액이 너무 큽니다.")
        Integer amount,

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotNull(message = "감정은 필수입니다.")
        Long emotionId,

        // memo 컬럼이 varchar(200) 이다. 넘기면 MySQL 이 데이터 절삭 오류를 낸다.
        @Size(max = 200, message = "메모는 200자를 넘을 수 없습니다.")
        String memo,

        @NotNull(message = "날짜는 필수입니다.")
        @PastOrPresent(message = "과거 또는 현재의 날짜여야 합니다.")
        LocalDateTime occurredAt,

        Long goalId
) {}
