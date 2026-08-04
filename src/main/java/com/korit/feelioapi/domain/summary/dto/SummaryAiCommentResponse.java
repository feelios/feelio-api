package com.korit.feelioapi.domain.summary.dto;

/** comment는 거래 없음·AI 실패 시 null이며, 그 경우에도 API는 성공 응답한다. */
public record SummaryAiCommentResponse(String comment) {
}
