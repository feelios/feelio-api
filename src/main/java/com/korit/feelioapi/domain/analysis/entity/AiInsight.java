package com.korit.feelioapi.domain.analysis.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * ai_insights 테이블 행 매핑 (순수 POJO).
 * 인사이트는 user_id·year·month 단위로 생성해 저장하고, 이후 조회는 이 테이블에서 읽는다(계약 §9).
 */
@Getter
@Setter
public class AiInsight {

    private Long insightId;
    private Long userId;
    private Integer year;
    private Integer month;
    /** 인사이트 종류. ai_insights.insight_type 은 varchar(20). */
    private String insightType;
    /** 사용자용 문장. ai_insights.content 는 varchar(500). */
    private String content;
    /** 재생성 판단 기준. 이번 달 인사이트가 오래되면 다시 만든다. */
    private LocalDateTime createdAt;
}
