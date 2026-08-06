package com.korit.feelioapi.global.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FCMService {

    public void sendMessage(String token, String title, String body, String url) {
        if (token == null || token.isBlank()) {
            log.warn("FCM token is empty, skipping push notification.");
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(token)
                    .putData("title", title)
                    .putData("body", body)
                    .putData("url", url != null ? url : "/record")
                    .setWebpushConfig(com.google.firebase.messaging.WebpushConfig.builder()
                            .putHeader("Urgency", "high")
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent FCM message: {}", response);
        } catch (Exception e) {
            log.error("Failed to send FCM message", e);
        }
    }
}
