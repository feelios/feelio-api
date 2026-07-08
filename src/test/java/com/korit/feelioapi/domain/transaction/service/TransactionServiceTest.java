package com.korit.feelioapi.domain.transaction.service;

import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionListResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.dto.TransactionTotalDto;
import com.korit.feelioapi.domain.transaction.mapper.TransactionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void 거래_목록과_합계를_조회한다() {
        Long userId = 1L;
        TransactionSearchCondition condition = new TransactionSearchCondition(2026, 7, null, null, null, null, null);
        List<TransactionDto> mockTransactions = List.of();
        TransactionTotalDto mockTotals = new TransactionTotalDto(2600000L, 320000L);

        when(transactionMapper.findTransactions(userId, condition)).thenReturn(mockTransactions);
        when(transactionMapper.calculateTotals(userId, condition)).thenReturn(mockTotals);

        TransactionListResponse response = transactionService.getTransactions(userId, condition);

        assertThat(response.transactions()).isEmpty();
        assertThat(response.totalIncome()).isEqualTo(2600000L);
        assertThat(response.totalExpense()).isEqualTo(320000L);

        verify(transactionMapper).findTransactions(userId, condition);
        verify(transactionMapper).calculateTotals(userId, condition);
    }
}
