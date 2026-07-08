package com.korit.feelioapi.domain.user.controller;

import com.korit.feelioapi.domain.user.dto.UpdateNicknameRequest;
import com.korit.feelioapi.domain.user.dto.UserResponse;
import com.korit.feelioapi.domain.user.service.UserService;
import com.korit.feelioapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 API (API-CONTRACT §4). 인증 필요(SecurityConfig anyRequest authenticated).
 * 모든 접근은 토큰 주체 user_id 기준 — 클라이언트가 보낸 userId 는 신뢰하지 않는다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /api/users/me — 내 정보 조회. */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(userService.getMe(userId));
    }

    /** PATCH /api/users/me — 닉네임 수정(1~8자). */
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMe(@AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody UpdateNicknameRequest request) {
        return ApiResponse.success(userService.updateNickname(userId, request.nickname()));
    }
}
