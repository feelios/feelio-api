package com.korit.feelioapi.domain.transaction.mapper;

import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.dto.TransactionTotalDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.korit.feelioapi.domain.transaction.entity.Transaction;

import java.util.List;

@Mapper
public interface TransactionMapper {
    List<TransactionDto> findTransactions(@Param("userId") Long userId, @Param("condition") TransactionSearchCondition condition);
    TransactionTotalDto calculateTotals(@Param("userId") Long userId, @Param("condition") TransactionSearchCondition condition);
    
    void insertTransaction(Transaction transaction);
    TransactionDto findTransactionById(@Param("transactionId") Long transactionId, @Param("userId") Long userId);
    
    Transaction findById(@Param("transactionId") Long transactionId);
    List<Transaction> findByIds(@Param("transactionIds") List<Long> transactionIds);
    void updateTransaction(Transaction transaction);
    void deleteTransaction(@Param("transactionId") Long transactionId);
    void deleteTransactionsBulk(@Param("transactionIds") List<Long> transactionIds);

    int deleteAllTransactionsByUserId(@Param("userId") Long userId);
}
