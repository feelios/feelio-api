package com.korit.feelioapi.domain.user.controller;

import com.korit.feelioapi.domain.user.entity.User;
import com.korit.feelioapi.domain.user.mapper.UserMapper;
import com.korit.feelioapi.global.response.ApiResponse;
import com.korit.feelioapi.global.service.FCMService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationTestController {

    private final FCMService fcmService;
    private final UserMapper userMapper;

    /** POST /api/notifications/test-fcm — 임시 테스트용 푸시 발송 API */
    @PostMapping("/test-fcm")
    public ApiResponse<Boolean> testFcm(@AuthenticationPrincipal Long userId,
                                        @RequestParam(defaultValue = "Feelio 알림 테스트") String title,
                                        @RequestParam(defaultValue = "알림이 정상적으로 도착했습니다!") String body) {
        User user = userMapper.findUserById(userId);
        if (user != null && user.getFcmToken() != null) {
            fcmService.sendMessage(user.getFcmToken(), title, body, "/");
            return ApiResponse.success(true);
        }
        return ApiResponse.success(false);
    }
}
