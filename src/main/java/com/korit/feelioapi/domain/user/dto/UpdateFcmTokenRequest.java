package com.korit.feelioapi.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateFcmTokenRequest {
    @NotBlank(message = "FCM 토큰은 필수입니다.")
    private String fcmToken;
}
