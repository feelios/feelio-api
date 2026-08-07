package com.korit.feelioapi.domain.meta.mapper;

import com.korit.feelioapi.domain.meta.entity.Category;
import com.korit.feelioapi.domain.meta.entity.Emotion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 마스터 데이터 접근 (순수 SQL — MetaMapper.xml 과 namespace/메서드명 일치).
 * is_active=true 만 정렬된 상태로 반환한다.
 */
@Mapper
public interface MetaMapper {

    List<Emotion> findActiveEmotions();

    List<Category> findActiveCategories();

    /**
     * 거래에 쓸 수 있는 카테고리인지 확인한다. 없으면 null.
     * 공용 카테고리(user_id IS NULL)와 요청자 본인의 커스텀 카테고리만 인정한다 —
     * 남의 커스텀 카테고리 ID 로도 저장되던 구멍을 막는다(#195).
     */
    Category findUsableCategory(@Param("categoryId") Long categoryId, @Param("userId") Long userId);

    /** 활성 감정인지 확인한다. 없으면 null. */
    Emotion findActiveEmotionById(@Param("emotionId") Long emotionId);
}
