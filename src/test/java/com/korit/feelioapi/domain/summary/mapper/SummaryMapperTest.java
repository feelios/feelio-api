package com.korit.feelioapi.domain.summary.mapper;

import com.korit.feelioapi.domain.meta.entity.Emotion;
import com.korit.feelioapi.domain.meta.mapper.MetaMapper;
import com.korit.feelioapi.domain.summary.dto.CalendarDayDto;
import com.korit.feelioapi.domain.transaction.entity.Transaction;
import com.korit.feelioapi.domain.transaction.mapper.TransactionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 캘린더 대표 감정이 '지출 기준'인지 확인한다.
 *
 * 감정 ID 는 환경마다 다르므로(#266) 하드코딩하지 않고 마스터에서 받아 쓴다.
 */
@SpringBootTest
@Transactional
class SummaryMapperTest {

    private static final Long USER_ID = 99998L;
    private static final int YEAR = 2099;
    private static final int MONTH = 3;

    @Autowired
    private SummaryMapper summaryMapper;

    @Autowired
    private TransactionMapper transactionMapper;

    @Autowired
    private MetaMapper metaMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long expenseEmotionId;
    private Long incomeEmotionId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0;");
        transactionMapper.deleteAllTransactionsByUserId(USER_ID);

        List<Emotion> emotions = metaMapper.findActiveEmotions();
        assertThat(emotions).hasSizeGreaterThanOrEqualTo(2);
        expenseEmotionId = emotions.get(0).getEmotionId();
        incomeEmotionId = emotions.get(1).getEmotionId();
    }

    @Test
    void 대표_감정은_수입_기록을_세지_않는다() {
        // 같은 날: 지출 1건(감정 A) + 수입 2건(감정 B).
        // 건수만 보면 수입 감정이 이기지만, 캘린더 색은 소비 회고라 지출 감정이어야 한다.
        insertTx("EXPENSE", LocalDateTime.of(YEAR, MONTH, 10, 9, 0), expenseEmotionId, 12_000);
        insertTx("INCOME", LocalDateTime.of(YEAR, MONTH, 10, 10, 0), incomeEmotionId, 500_000);
        insertTx("INCOME", LocalDateTime.of(YEAR, MONTH, 10, 11, 0), incomeEmotionId, 500_000);

        CalendarDayDto day = findDay(LocalDate.of(YEAR, MONTH, 10));

        assertThat(day).isNotNull();
        assertThat(day.getDominantEmotion().getEmotionId()).isEqualTo(expenseEmotionId);
        // 건수·합계도 같은 기준이어야 한다 — 지출 1건, 12,000원
        assertThat(day.getTransactionCount()).isEqualTo(1);
        assertThat(day.getTotalExpense()).isEqualTo(12_000L);
    }

    @Test
    void 수입만_있는_날은_감정_색을_갖지_않는다() {
        insertTx("INCOME", LocalDateTime.of(YEAR, MONTH, 11, 10, 0), incomeEmotionId, 2_600_000);

        assertThat(findDay(LocalDate.of(YEAR, MONTH, 11))).isNull();
    }

    @Test
    void 건수가_같으면_금액이_큰_감정이_그날을_대표한다() {
        // 하루에 한 건씩 두 감정 — 건수는 1:1 이다.
        // 나중에 적은 쪽(B, 2만원)이 아니라 금액이 큰 쪽(A, 34만원)이 대표여야 한다.
        insertTx("EXPENSE", LocalDateTime.of(YEAR, MONTH, 12, 10, 42), expenseEmotionId, 343_434);
        insertTx("EXPENSE", LocalDateTime.of(YEAR, MONTH, 12, 14, 25), incomeEmotionId, 24_000);

        CalendarDayDto day = findDay(LocalDate.of(YEAR, MONTH, 12));

        assertThat(day).isNotNull();
        assertThat(day.getDominantEmotion().getEmotionId()).isEqualTo(expenseEmotionId);
    }

    private CalendarDayDto findDay(LocalDate date) {
        return summaryMapper.findCalendarSummary(USER_ID, YEAR, MONTH).stream()
                .filter(day -> date.equals(day.getDate()))
                .findFirst()
                .orElse(null);
    }

    private void insertTx(String type, LocalDateTime occurredAt, Long emotionId, int amount) {
        Transaction tx = new Transaction();
        tx.setUserId(USER_ID);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setOccurredAt(occurredAt);
        tx.setEmotionId(emotionId);
        tx.setCategoryId(1L);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setUpdatedAt(LocalDateTime.now());
        transactionMapper.insertTransaction(tx);
    }
}
