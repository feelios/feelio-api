package com.korit.feelioapi.domain.meta.dto;

import java.util.List;

/**
 * GET /api/meta 응답 data (API-CONTRACT §5).
 */
public record MetaResponse(
        List<EmotionResponse> emotions,
        List<CategoryResponse> categories
) {
}
