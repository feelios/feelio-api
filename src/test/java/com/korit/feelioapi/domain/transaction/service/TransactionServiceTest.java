package com.korit.feelioapi.domain.transaction.service;

import com.korit.feelioapi.domain.transaction.dto.TransactionCreateRequest;
import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionListResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionResetResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.dto.TransactionTotalDto;
import com.korit.feelioapi.domain.transaction.entity.Transaction;
import com.korit.feelioapi.domain.transaction.mapper.TransactionMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
        TransactionSearchCondition condition = new TransactionSearchCondition(2026, 7, null, null, null, null, null, null);
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

    @Test
    void 거래_기록을_생성하고_생성된_객체를_반환한다() {
        Long userId = 1L;
        TransactionCreateRequest request = new TransactionCreateRequest("EXPENSE", 10000, 2L, 3L, "memo", LocalDateTime.now(), null);
        TransactionDto mockDto = new TransactionDto(10L, "EXPENSE", 10000, "memo", request.occurredAt(), null, null, null);

        doAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setTransactionId(10L);
            return null;
        }).when(transactionMapper).insertTransaction(any(Transaction.class));

        when(transactionMapper.findTransactionById(10L, userId)).thenReturn(mockDto);

        TransactionDto result = transactionService.createTransaction(userId, request);

        assertThat(result.transactionId()).isEqualTo(10L);
        verify(transactionMapper).insertTransaction(any(Transaction.class));
        verify(transactionMapper).findTransactionById(10L, userId);
    }

    @Test
    void 거래_기록을_삭제하고_deleted_true를_반환한다() {
        Long userId = 1L;
        Long transactionId = 10L;
        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setUserId(userId);

        when(transactionMapper.findById(transactionId)).thenReturn(transaction);

        var response = transactionService.deleteTransaction(userId, transactionId);

        assertThat(response.deleted()).isTrue();
        verify(transactionMapper).findById(transactionId);
        verify(transactionMapper).deleteTransaction(transactionId);
    }

    @Test
    void 거래_상세를_조회한다() {
        Long userId = 1L;
        Long transactionId = 10L;
        Transaction owned = new Transaction();
        owned.setTransactionId(transactionId);
        owned.setUserId(userId);
        TransactionDto mockDto = new TransactionDto(transactionId, "EXPENSE", 10000, "memo", LocalDateTime.now(), null, null, null);

        when(transactionMapper.findById(transactionId)).thenReturn(owned);
        when(transactionMapper.findTransactionById(transactionId, userId)).thenReturn(mockDto);

        TransactionDto result = transactionService.getTransaction(userId, transactionId);

        assertThat(result.transactionId()).isEqualTo(transactionId);
        verify(transactionMapper).findTransactionById(transactionId, userId);
    }

    @Test
    void 거래를_수정하고_갱신된_객체를_반환한다() {
        Long userId = 1L;
        Long transactionId = 10L;
        Transaction owned = new Transaction();
        owned.setTransactionId(transactionId);
        owned.setUserId(userId);
        TransactionCreateRequest request = new TransactionCreateRequest("INCOME", 50000, 9L, 1L, "수정메모", LocalDateTime.now(), null);
        TransactionDto updated = new TransactionDto(transactionId, "INCOME", 50000, "수정메모", request.occurredAt(), null, null, null);

        when(transactionMapper.findById(transactionId)).thenReturn(owned);
        when(transactionMapper.findTransactionById(transactionId, userId)).thenReturn(updated);

        TransactionDto result = transactionService.updateTransaction(userId, transactionId, request);

        assertThat(result.type()).isEqualTo("INCOME");
        assertThat(result.amount()).isEqualTo(50000);
        verify(transactionMapper).updateTransaction(any(Transaction.class));
        verify(transactionMapper).findTransactionById(transactionId, userId);
    }

    @Test
    void 거래_수정시_존재하지_않으면_NOT_FOUND() {
        Long userId = 1L;
        Long transactionId = 10L;
        TransactionCreateRequest request = new TransactionCreateRequest("EXPENSE", 10000, 2L, 3L, "memo", LocalDateTime.now(), null);

        when(transactionMapper.findById(transactionId)).thenReturn(null);

        assertThatThrownBy(() -> transactionService.updateTransaction(userId, transactionId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);

        verify(transactionMapper, org.mockito.Mockito.never()).updateTransaction(any(Transaction.class));
    }

    @Test
    void 타인의_거래를_수정하려_하면_FORBIDDEN() {
        Long userId = 1L;
        Long transactionId = 10L;
        Transaction others = new Transaction();
        others.setTransactionId(transactionId);
        others.setUserId(2L);
        TransactionCreateRequest request = new TransactionCreateRequest("EXPENSE", 10000, 2L, 3L, "memo", LocalDateTime.now(), null);

        when(transactionMapper.findById(transactionId)).thenReturn(others);

        assertThatThrownBy(() -> transactionService.updateTransaction(userId, transactionId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);

        verify(transactionMapper, org.mockito.Mockito.never()).updateTransaction(any(Transaction.class));
    }

    @Test
    void 거래_삭제시_존재하지_않으면_NOT_FOUND() {
        Long userId = 1L;
        Long transactionId = 10L;

        when(transactionMapper.findById(transactionId)).thenReturn(null);

        assertThatThrownBy(() -> transactionService.deleteTransaction(userId, transactionId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 타인의_거래를_삭제하려_하면_FORBIDDEN() {
        Long userId = 1L;
        Long transactionId = 10L;
        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setUserId(2L); // Other user

        when(transactionMapper.findById(transactionId)).thenReturn(transaction);

        assertThatThrownBy(() -> transactionService.deleteTransaction(userId, transactionId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
    }
}
