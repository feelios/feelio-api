package com.korit.feelioapi.domain.meta.service;

import com.korit.feelioapi.domain.meta.dto.CategoryResponse;
import com.korit.feelioapi.domain.meta.dto.EmotionResponse;
import com.korit.feelioapi.domain.meta.dto.MetaResponse;
import com.korit.feelioapi.domain.meta.mapper.MetaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 마스터 데이터 조회(API-CONTRACT §5). 개인 데이터가 아니라 user_id 무관.
 * is_active=true 인 감정·카테고리만 정렬된 상태로 매퍼에서 받아 응답 DTO 로 변환한다.
 */
@Service
@RequiredArgsConstructor
public class MetaService {

    private final MetaMapper metaMapper;

    @Transactional(readOnly = true)
    public MetaResponse getMeta() {
        List<EmotionResponse> emotions = metaMapper.findActiveEmotions().stream()
                .map(EmotionResponse::of)
                .toList();
        List<CategoryResponse> categories = metaMapper.findActiveCategories().stream()
                .map(CategoryResponse::of)
                .toList();
        return new MetaResponse(emotions, categories);
    }
}
