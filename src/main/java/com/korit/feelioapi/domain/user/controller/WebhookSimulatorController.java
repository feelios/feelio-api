package com.korit.feelioapi.domain.user.controller;

import com.korit.feelioapi.domain.user.entity.User;
import com.korit.feelioapi.domain.user.mapper.UserMapper;
import com.korit.feelioapi.global.response.ApiResponse;
import com.korit.feelioapi.global.service.FCMService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookSimulatorController {

    private final FCMService fcmService;
    private final UserMapper userMapper;

    @Data
    public static class PaymentRequest {
        private String merchant;
        private int amount;
        private Long userId;
    }

    /** POST /api/webhooks/card-payment — 임시 결제 웹훅 시뮬레이터 API */
    @PostMapping("/card-payment")
    public ApiResponse<Boolean> handleCardPayment(@RequestBody PaymentRequest req) {
        // 실제 결제 저장 로직은 생략하고 FCM 푸시만 시뮬레이션
        User user = userMapper.findUserById(req.getUserId());
        
        if (user != null && user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
            String title = req.getMerchant() + " 결제 알림";
            String body = "지금 이 소비 어떠셨나요? 클릭해서 바로 기록해요.";
            String url = "/record?amount=" + req.getAmount() + "&merchant=" + req.getMerchant();
            fcmService.sendMessage(user.getFcmToken(), title, body, url);
            
            return ApiResponse.success(true);
        }
        
        return ApiResponse.success(false);
    }
}
