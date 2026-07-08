package com.korit.feelioapi.domain.transaction.mapper;

import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.dto.TransactionTotalDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TransactionMapper {
    List<TransactionDto> findTransactions(@Param("userId") Long userId, @Param("condition") TransactionSearchCondition condition);
    TransactionTotalDto calculateTotals(@Param("userId") Long userId, @Param("condition") TransactionSearchCondition condition);
}
