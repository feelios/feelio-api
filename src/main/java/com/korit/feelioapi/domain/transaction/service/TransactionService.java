package com.korit.feelioapi.domain.transaction.service;

import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionListResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.dto.TransactionTotalDto;
import com.korit.feelioapi.domain.transaction.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public TransactionListResponse getTransactions(Long userId, TransactionSearchCondition condition) {
        List<TransactionDto> transactions = transactionMapper.findTransactions(userId, condition);
        TransactionTotalDto totals = transactionMapper.calculateTotals(userId, condition);

        return new TransactionListResponse(
                transactions,
                totals.totalIncome(),
                totals.totalExpense()
        );
    }
}
