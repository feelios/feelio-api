package com.korit.feelioapi.domain.meta.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * emotions 테이블 행 매핑 (순수 POJO — JPA 아님).
 * 고정 8종 마스터. 응답에는 character_key·is_active 를 노출하지 않는다.
 */
@Getter
@Setter
public class Emotion {

    private Long emotionId;
    private String name;
    private String color;
    private String characterKey;
    private int sortOrder;
    private boolean isActive;
}
