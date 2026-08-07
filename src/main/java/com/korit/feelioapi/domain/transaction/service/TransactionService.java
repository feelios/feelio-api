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
import com.korit.feelioapi.domain.meta.entity.Category;
import com.korit.feelioapi.domain.meta.mapper.MetaMapper;
import com.korit.feelioapi.domain.analysis.service.EmotionAnalysisService;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import com.korit.feelioapi.domain.analysis.mapper.AnalysisMapper;
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
    private final AnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;

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

        validateReferences(userId, request);
        transaction.setGoalId(request.goalId());

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

        validateReferences(userId, request);
        transaction.setGoalId(request.goalId());

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
        com.korit.feelioapi.domain.analysis.entity.AiInsight insight = analysisMapper.findInsightByType(userId, 0, 0, "PATTERN");
        if (insight == null || insight.getContent() == null) {
            return new TransactionPatternResponse(new TransactionPatternDto(0, null, null, null, null, null, java.util.List.of()), java.util.List.of());
        }

        try {
            TransactionPatternDto dto = objectMapper.readValue(insight.getContent(), TransactionPatternDto.class);
            return new TransactionPatternResponse(dto, dto.evidence() == null ? java.util.List.of() : dto.evidence());
        } catch (Exception e) {
            return new TransactionPatternResponse(new TransactionPatternDto(0, null, null, null, null, null, java.util.List.of()), java.util.List.of());
        }
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

    /**
     * 요청 본문이 가리키는 ID 들이 실제로 쓸 수 있는 값인지 확인한다 (#195).
     *
     * 예전에는 categoryId·emotionId 를 그대로 INSERT 해서, 없는 ID 가 오면 FK 위반이
     * SQL 예외로 터져 500 이 나갔다. 프론트는 원인을 알 수 없고 로그에는 스택만 쌓였다.
     * 저장 전에 조회해서 계약 §6 대로 VALIDATION_ERROR(400) 로 돌려준다.
     *
     * goalId 도 여기로 합쳤다. 기존에는 NOT_FOUND(404) 였는데, 계약 §6 의 POST 는
     * VALIDATION_ERROR 만 규정한다 — 없는 목표를 가리키는 건 본문이 잘못된 것이지
     * 요청한 리소스가 없는 게 아니다. (PUT 의 NOT_FOUND 는 경로의 transactionId 몫이다.)
     */
    private void validateReferences(Long userId, TransactionCreateRequest request) {
        Category category = metaMapper.findUsableCategory(request.categoryId(), userId);
        if (category == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "존재하지 않는 카테고리입니다.");
        }
        // 지출에 수입 카테고리를 붙이면 합계·월별 집계가 조용히 어긋난다. 여기서 끊는다.
        if (!category.getType().equals(request.type())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    String.format("'%s'은(는) %s 카테고리라 이 거래에 쓸 수 없습니다.",
                            category.getName(), category.getType()));
        }

        if (metaMapper.findActiveEmotionById(request.emotionId()) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "존재하지 않는 감정입니다.");
        }

        if (request.goalId() != null) {
            Goal goal = goalMapper.findById(request.goalId());
            if (goal == null || !goal.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "존재하지 않는 목표입니다.");
            }
        }
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
