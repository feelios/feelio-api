package com.korit.feelioapi.domain.meta.mapper;

import com.korit.feelioapi.domain.meta.entity.Category;
import com.korit.feelioapi.domain.meta.entity.Emotion;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 마스터 데이터 접근 (순수 SQL — MetaMapper.xml 과 namespace/메서드명 일치).
 * is_active=true 만 정렬된 상태로 반환한다.
 */
@Mapper
public interface MetaMapper {

    List<Emotion> findActiveEmotions();

    List<Category> findActiveCategories();
}
