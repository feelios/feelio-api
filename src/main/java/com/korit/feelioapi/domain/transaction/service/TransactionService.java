package com.korit.feelioapi.domain.transaction.service;

import com.korit.feelioapi.domain.transaction.dto.TransactionCreateRequest;
import com.korit.feelioapi.domain.transaction.dto.TransactionDeleteResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionListResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionPatternDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionPatternResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionResetResponse;
import com.korit.feelioapi.domain.transaction.dto.TransactionSearchCondition;
import com.korit.feelioapi.domain.transaction.dto.TransactionTotalDto;
import com.korit.feelioapi.domain.transaction.entity.Transaction;
import com.korit.feelioapi.domain.goal.entity.Goal;
import com.korit.feelioapi.domain.goal.mapper.GoalMapper;
import com.korit.feelioapi.domain.transaction.mapper.TransactionMapper;
import com.korit.feelioapi.domain.user.mapper.UserMapper;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionMapper transactionMapper;
    private final GoalMapper goalMapper;
    private final UserMapper userMapper;

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

    @Transactional
    public TransactionDto createTransaction(Long userId, TransactionCreateRequest request) {
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setEmotionId(request.emotionId());
        transaction.setCategoryId(request.categoryId());
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setMemo(request.memo());
        transaction.setOccurredAt(request.occurredAt());

        if (request.goalId() != null) {
            Goal goal = goalMapper.findById(request.goalId());
            if (goal == null || !goal.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            transaction.setGoalId(request.goalId());
        }

        transactionMapper.insertTransaction(transaction);

        return transactionMapper.findTransactionById(transaction.getTransactionId(), userId);
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransaction(Long userId, Long transactionId) {
        getOwnedOrThrow(userId, transactionId);
        return transactionMapper.findTransactionById(transactionId, userId);
    }

    @Transactional
    public TransactionDto updateTransaction(Long userId, Long transactionId, TransactionCreateRequest request) {
        getOwnedOrThrow(userId, transactionId);

        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setEmotionId(request.emotionId());
        transaction.setCategoryId(request.categoryId());
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setMemo(request.memo());
        transaction.setOccurredAt(request.occurredAt());

        if (request.goalId() != null) {
            Goal goal = goalMapper.findById(request.goalId());
            if (goal == null || !goal.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            transaction.setGoalId(request.goalId());
        } else {
            transaction.setGoalId(null);
        }
        transactionMapper.updateTransaction(transaction);

        return transactionMapper.findTransactionById(transactionId, userId);
    }

    @Transactional
    public TransactionDeleteResponse deleteTransaction(Long userId, Long transactionId) {
        getOwnedOrThrow(userId, transactionId);
        transactionMapper.deleteTransaction(transactionId);
        return new TransactionDeleteResponse(true);
    }

    @Transactional
    public TransactionDeleteResponse deleteTransactionsBulk(Long userId, List<Long> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return new TransactionDeleteResponse(true);
        }
        List<Transaction> transactions = transactionMapper.findByIds(transactionIds);
        if (transactions.size() != transactionIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        for (Transaction t : transactions) {
            if (!t.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        transactionMapper.deleteTransactionsBulk(transactionIds);
        return new TransactionDeleteResponse(true);
    }

    @Transactional
    public TransactionResetResponse resetTransactions(Long userId) {
        int deletedCount = transactionMapper.deleteAllTransactionsByUserId(userId);
        return new TransactionResetResponse(deletedCount);
    }

    @Transactional(readOnly = true)
    public TransactionListResponse getPendingDutchPay(Long userId) {
        List<TransactionDto> transactions = transactionMapper.findPendingDutchPay(userId);
        return new TransactionListResponse(transactions, 0L, 0L);
    }

    @Transactional
    public com.korit.feelioapi.domain.transaction.dto.DutchPaySettleResponse settleDutchPay(Long userId, Long transactionId) {
        Transaction transaction = getOwnedOrThrow(userId, transactionId);
        
        if (transaction.isSettled()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        // 1. 원본 지출 정산 완료 처리
        transaction.setSettled(true);
        transactionMapper.updateTransaction(transaction);

        // 2. 신규 정산금(INCOME) 거래 생성
        Long categoryId = transactionMapper.findCategoryIdByNameAndType("정산금", "INCOME");
        if (categoryId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR); // 시드 데이터 누락
        }

        Transaction incomeTransaction = new Transaction();
        incomeTransaction.setUserId(userId);
        incomeTransaction.setEmotionId(transaction.getEmotionId());
        incomeTransaction.setCategoryId(categoryId);
        incomeTransaction.setType("INCOME");
        incomeTransaction.setAmount(transaction.getAmount());
        incomeTransaction.setMemo(transaction.getMemo() + " (정산완료)");
        incomeTransaction.setOccurredAt(java.time.LocalDateTime.now());
        transactionMapper.insertTransaction(incomeTransaction);

        // 3. 총자산 증가
        userMapper.addTotalAsset(userId, transaction.getAmount());

        return new com.korit.feelioapi.domain.transaction.dto.DutchPaySettleResponse(true, incomeTransaction.getTransactionId());
    }

    @Transactional(readOnly = true)
    public TransactionPatternResponse getRecurringPatterns(Long userId) {
        List<Transaction> expenses = transactionMapper.findExpensesForPattern(userId);

        List<Transaction> merged = new java.util.ArrayList<>();
        for (Transaction current : expenses) {
            if (merged.isEmpty()) {
                merged.add(cloneTransaction(current));
            } else {
                Transaction last = merged.get(merged.size() - 1);
                long Math_abs_diff = java.time.Duration.between(last.getOccurredAt(), current.getOccurredAt()).abs().toMinutes();
                
                if (Math_abs_diff <= 5 && java.util.Objects.equals(last.getMemo(), current.getMemo())) {
                    last.setAmount(last.getAmount() + current.getAmount());
                } else {
                    merged.add(cloneTransaction(current));
                }
            }
        }

        java.util.Map<String, TransactionPatternDto> patternsMap = new java.util.HashMap<>();
        for (Transaction t : merged) {
            String timeSlot = getTimeSlot(t.getOccurredAt().getHour());
            String key = t.getEmotionId() + ":" + timeSlot + ":" + t.getMemo();
            
            TransactionPatternDto existing = patternsMap.get(key);
            if (existing == null) {
                patternsMap.put(key, new TransactionPatternDto(timeSlot, t.getEmotionId(), t.getMemo(), 1, t.getAmount()));
            } else {
                patternsMap.put(key, new TransactionPatternDto(timeSlot, t.getEmotionId(), t.getMemo(), existing.count() + 1, existing.totalAmount() + t.getAmount()));
            }
        }

        List<TransactionPatternDto> result = patternsMap.values().stream()
                .filter(p -> p.count() >= 2)
                .sorted(java.util.Comparator.comparingInt(TransactionPatternDto::count).reversed())
                .toList();

        return new TransactionPatternResponse(result);
    }

    private Transaction cloneTransaction(Transaction t) {
        Transaction cloned = new Transaction();
        cloned.setTransactionId(t.getTransactionId());
        cloned.setUserId(t.getUserId());
        cloned.setEmotionId(t.getEmotionId());
        cloned.setCategoryId(t.getCategoryId());
        cloned.setType(t.getType());
        cloned.setAmount(t.getAmount());
        cloned.setMemo(t.getMemo());
        cloned.setOccurredAt(t.getOccurredAt());
        return cloned;
    }

    private String getTimeSlot(int hour) {
        if (hour >= 0 && hour < 6) return "DAWN";
        if (hour >= 6 && hour < 12) return "MORNING";
        if (hour >= 12 && hour < 18) return "AFTERNOON";
        return "NIGHT";
    }

    /** 대상 존재 + 본인 소유 검증 (계약 §6: 없음 NOT_FOUND / 타인 FORBIDDEN). */
    private Transaction getOwnedOrThrow(Long userId, Long transactionId) {
        Transaction transaction = transactionMapper.findById(transactionId);
        if (transaction == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!transaction.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return transaction;
    }
}
