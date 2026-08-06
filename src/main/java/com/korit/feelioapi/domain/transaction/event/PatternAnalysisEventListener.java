package com.korit.feelioapi.domain.transaction.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.korit.feelioapi.domain.analysis.service.AiInsightStore;
import com.korit.feelioapi.domain.analysis.service.EmotionAnalysisService;
import com.korit.feelioapi.domain.meta.entity.Emotion;
import com.korit.feelioapi.domain.meta.mapper.MetaMapper;
import com.korit.feelioapi.domain.transaction.dto.PatternKeyDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionDto;
import com.korit.feelioapi.domain.transaction.dto.TransactionPatternDto;
import com.korit.feelioapi.domain.transaction.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatternAnalysisEventListener {

    private final TransactionMapper transactionMapper;
    private final MetaMapper metaMapper;
    private final EmotionAnalysisService emotionAnalysisService;
    private final AiInsightStore aiInsightStore;
    private final ObjectMapper objectMapper;

    @Async
    @EventListener
    public void handleTransactionChangedEvent(TransactionChangedEvent event) {
        Long userId = event.userId();
        log.info("[Async Pattern Analysis] Start processing for userId: {}", userId);

        try {
            // 1. RDBMS를 이용해 가장 반복되는 소비 패턴 추출
            PatternKeyDto pattern = transactionMapper.findTopRecurringPattern(userId);

            if (pattern == null || pattern.count() < 2) {
                // 패턴이 없거나 2회 미만이면 빈 데이터를 저장하여 초기화
                aiInsightStore.replaceByType(userId, 0, 0, "PATTERN", null);
                log.info("[Async Pattern Analysis] No significant pattern found for userId: {}", userId);
                return;
            }

            // 2. 해당 패턴의 원본 지출 내역(Evidence) 조회
            List<TransactionDto> evidence = transactionMapper.findEvidenceForPattern(
                    userId, pattern.emotionId(), pattern.categoryId(), pattern.timeSlot()
            );

            // 3. 감정 이름 및 카테고리 이름 파싱
            Map<Long, String> emotions = metaMapper.findActiveEmotions().stream()
                    .collect(Collectors.toMap(Emotion::getEmotionId, Emotion::getName));
            String emotionName = emotions.getOrDefault(pattern.emotionId(), "알 수 없는 감정");
            String categoryName = evidence.isEmpty() ? "알 수 없는 카테고리" : evidence.get(0).category().name();

            String timeStr = switch(pattern.timeSlot()) {
                case "MORNING" -> "아침";
                case "AFTERNOON" -> "낮";
                case "NIGHT" -> "밤";
                default -> "새벽";
            };
            String title = emotionName + "일 때 " + categoryName + " 지출 패턴";

            // 4. AI 조언 생성 (GPT 호출)
            String aiAdvice = emotionAnalysisService.generatePattern(emotionName, categoryName, timeStr, pattern.count());

            // 5. 프론트엔드 반환용 DTO 구성
            TransactionPatternDto dto = new TransactionPatternDto(
                    pattern.count(),
                    title,
                    emotionName,
                    categoryName,
                    timeStr,
                    aiAdvice,
                    evidence
            );

            // 6. JSON 직렬화 후 저장 (year=0, month=0)
            String jsonContent = objectMapper.writeValueAsString(dto);
            aiInsightStore.replaceByType(userId, 0, 0, "PATTERN", jsonContent);

            log.info("[Async Pattern Analysis] Successfully analyzed and cached pattern for userId: {}", userId);

        } catch (Exception e) {
            log.error("[Async Pattern Analysis] Error processing pattern for userId: {}", userId, e);
        }
    }
}
