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
import com.korit.feelioapi.domain.meta.mapper.MetaMapper;
import com.korit.feelioapi.domain.analysis.service.EmotionAnalysisService;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import com.korit.feelioapi.domain.transaction.event.TransactionChangedEvent;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionMapper transactionMapper;
    private final GoalMapper goalMapper;
    private final UserMapper userMapper;
    private final MetaMapper metaMapper;
    private final EmotionAnalysisService emotionAnalysisService;
    private final ApplicationEventPublisher eventPublisher;

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

        LocalDateTime now = LocalDateTime.now();
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);

        transactionMapper.insertTransaction(transaction);

        eventPublisher.publishEvent(new TransactionChangedEvent(userId));

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
        
        transaction.setUpdatedAt(LocalDateTime.now());
        
        transactionMapper.updateTransaction(transaction);

        eventPublisher.publishEvent(new TransactionChangedEvent(userId));

        return transactionMapper.findTransactionById(transactionId, userId);
    }

    @Transactional
    public TransactionDeleteResponse deleteTransaction(Long userId, Long transactionId) {
        getOwnedOrThrow(userId, transactionId);
        transactionMapper.deleteTransaction(transactionId);
        eventPublisher.publishEvent(new TransactionChangedEvent(userId));
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
        eventPublisher.publishEvent(new TransactionChangedEvent(userId));
        return new TransactionDeleteResponse(true);
    }

    @Transactional
    public TransactionResetResponse resetTransactions(Long userId) {
        int deletedCount = transactionMapper.deleteAllTransactionsByUserId(userId);
        eventPublisher.publishEvent(new TransactionChangedEvent(userId));
        return new TransactionResetResponse(deletedCount);
    }

    @Transactional
    public Object mergeTransaction(Long userId, Long transactionId, Integer receivedAmount) {
        Transaction transaction = getOwnedOrThrow(userId, transactionId);

        int finalAmount = transaction.getAmount() - receivedAmount;
        if (finalAmount <= 0) {
            transaction.setAmount(0);
        } else {
            transaction.setAmount(finalAmount);
        }
        transactionMapper.updateTransaction(transaction);
        
        eventPublisher.publishEvent(new TransactionChangedEvent(userId));

        return transactionMapper.findTransactionById(transactionId, userId);
    }

    @Transactional(readOnly = true)
    public TransactionPatternResponse getRecurringPatterns(Long userId) {
        List<Transaction> expenses = transactionMapper.findExpensesForPattern(userId);

        record PatternKey(Long emotionId, Long categoryId, String timeSlot) {}
        record PatternVal(int count, int totalAmount) {}
        
        java.util.Map<PatternKey, PatternVal> patternsMap = new java.util.HashMap<>();
        for (Transaction t : expenses) {
            String timeSlot = getTimeSlot(t.getOccurredAt().getHour());
            PatternKey key = new PatternKey(t.getEmotionId(), t.getCategoryId(), timeSlot);
            PatternVal existing = patternsMap.get(key);
            if (existing == null) {
                patternsMap.put(key, new PatternVal(1, t.getAmount()));
            } else {
                patternsMap.put(key, new PatternVal(existing.count() + 1, existing.totalAmount() + t.getAmount()));
            }
        }

        var topEntry = patternsMap.entrySet().stream()
                .filter(e -> e.getValue().count() >= 2)
                .max(java.util.Comparator.comparingInt(e -> e.getValue().count()));

        if (topEntry.isEmpty()) {
            return new TransactionPatternResponse(new TransactionPatternDto(0, null, null, null, null, null));
        }

        var key = topEntry.get().getKey();
        int count = topEntry.get().getValue().count();

        java.util.Map<Long, String> emotions = metaMapper.findActiveEmotions().stream()
                .collect(java.util.stream.Collectors.toMap(com.korit.feelioapi.domain.meta.entity.Emotion::getEmotionId, com.korit.feelioapi.domain.meta.entity.Emotion::getName));
        java.util.Map<Long, String> categories = metaMapper.findActiveCategories().stream()
                .collect(java.util.stream.Collectors.toMap(com.korit.feelioapi.domain.meta.entity.Category::getCategoryId, com.korit.feelioapi.domain.meta.entity.Category::getName));

        String emotionName = emotions.getOrDefault(key.emotionId(), "알수없음");
        String categoryName = categories.getOrDefault(key.categoryId(), "알수없음");
        
        String title = emotionName + "일 때 " + categoryName + " 지출 패턴";
        String timeStr = switch(key.timeSlot()) {
            case "MORNING" -> "아침";
            case "AFTERNOON" -> "낮";
            case "NIGHT" -> "밤";
            default -> "새벽";
        };
        String desc = emotionAnalysisService.generatePattern(emotionName, categoryName, timeStr, count);

        TransactionPatternDto dto = new TransactionPatternDto(
                count, title, emotionName, categoryName, timeStr, desc
        );

        return new TransactionPatternResponse(dto);
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
