package com.korit.feelioapi.domain.transaction.mapper;

import com.korit.feelioapi.domain.transaction.dto.PatternKeyDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.entity.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TransactionMapperTest {

    @Autowired
    private TransactionMapper transactionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findTopRecurringPattern_groupsByEmotionCategoryAndTimeSlot() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
        Long userId = 99999L;
        // Clean up any existing transactions for this test user
        transactionMapper.deleteAllTransactionsByUserId(userId);

        // 1. Dawn (0-5), Emotion 1, Category 2 (Count: 1)
        insertTx(userId, LocalDateTime.of(2026, 8, 6, 2, 0), 1L, 2L, 5000);

        // 2. Night (18-23), Emotion 2, Category 3 (Count: 3, Total: 15000)
        insertTx(userId, LocalDateTime.of(2026, 8, 6, 20, 0), 2L, 3L, 5000);
        insertTx(userId, LocalDateTime.of(2026, 8, 6, 21, 0), 2L, 3L, 5000);
        insertTx(userId, LocalDateTime.of(2026, 8, 6, 22, 0), 2L, 3L, 5000);

        // 3. Morning (6-11), Emotion 1, Category 2 (Count: 2, Total: 12000)
        insertTx(userId, LocalDateTime.of(2026, 8, 6, 8, 0), 1L, 2L, 6000);
        insertTx(userId, LocalDateTime.of(2026, 8, 6, 9, 0), 1L, 2L, 6000);

        PatternKeyDto pattern = transactionMapper.findTopRecurringPattern(userId);

        assertThat(pattern).isNotNull();
        assertThat(pattern.timeSlot()).isEqualTo("NIGHT");
        assertThat(pattern.emotionId()).isEqualTo(2L);
        assertThat(pattern.categoryId()).isEqualTo(3L);
        assertThat(pattern.count()).isEqualTo(3);
        assertThat(pattern.totalAmount()).isEqualTo(15000);

        // Verify Evidence fetching
        List<TransactionDto> evidence = transactionMapper.findEvidenceForPattern(userId, 2L, 3L, "NIGHT");
        assertThat(evidence).hasSize(3);
    }

    private void insertTx(Long userId, LocalDateTime occurredAt, Long emotionId, Long categoryId, int amount) {
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setType("EXPENSE");
        tx.setAmount(amount);
        tx.setOccurredAt(occurredAt);
        tx.setEmotionId(emotionId);
        tx.setCategoryId(categoryId);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());
        transactionMapper.insertTransaction(tx);
    }
}
