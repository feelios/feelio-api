package com.korit.feelioapi.domain.meta.controller;

import com.korit.feelioapi.domain.meta.dto.MetaResponse;
import com.korit.feelioapi.domain.meta.service.MetaService;
import com.korit.feelioapi.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마스터 데이터 API (API-CONTRACT §5). 인증 필요(SecurityConfig anyRequest authenticated).
 * 감정·카테고리 마스터를 프론트 폼/필터가 세션 캐시로 쓴다.
 */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final MetaService metaService;

    /** GET /api/meta — 활성 감정·카테고리 마스터 조회. */
    @GetMapping
    public ApiResponse<MetaResponse> getMeta() {
        return ApiResponse.success(metaService.getMeta());
    }
}
