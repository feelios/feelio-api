package com.korit.feelioapi.domain.analysis.service;

import com.korit.feelioapi.domain.analysis.dto.InsightDto;
import com.korit.feelioapi.domain.analysis.mapper.AnalysisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ai_insights 저장 전담. AnalysisService 에서 분리한 이유는 두 가지다.
 * 1) 삭제+삽입을 한 트랜잭션으로 묶어야 하는데, 같은 클래스 안에서 호출하면 @Transactional 이 프록시를 안 타서 무시된다.
 * 2) AnalysisService 의 조회 경로는 외부(GPT) 호출을 포함해 트랜잭션으로 감싸면 안 된다.
 */
@Service
@RequiredArgsConstructor
public class AiInsightStore {

    private final AnalysisMapper analysisMapper;

    /**
     * 해당 연·월 인사이트를 통째로 교체한다.
     * INSERT 만 하면 동시 요청 시 두 벌이 쌓이는데, 지우고 넣으면 나중 것이 앞 것을 덮어써 항상 한 벌만 남는다.
     * (user_id, year, month) 는 다건이라 유니크 제약을 걸 수 없어 이 방식으로 멱등성을 만든다.
     */
    @Transactional
    public void replace(Long userId, int year, int month, List<InsightDto> insights) {
        analysisMapper.deleteInsights(userId, year, month);
        analysisMapper.insertInsights(userId, year, month, insights);
    }
}
