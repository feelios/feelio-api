package com.korit.feelioapi.domain.auth.controller;

import com.korit.feelioapi.domain.auth.dto.TokenRefreshResponse;
import com.korit.feelioapi.domain.auth.service.AuthService;
import com.korit.feelioapi.global.exception.BusinessException;
import com.korit.feelioapi.global.exception.ErrorCode;
import com.korit.feelioapi.global.security.AuthCookieManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 토큰 재발급 입구 검증 (#178).
 * 쿠키 유무 판정이 컨트롤러에 있어 서비스 단위 테스트로는 덮이지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthCookieManager authCookieManager;

    @InjectMocks
    private AuthController authController;

    @Test
    void 리프레시_쿠키가_없으면_UNAUTHORIZED() {
        assertThatThrownBy(() -> authController.refreshToken(null, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);

        // 500 으로 새지 않도록, 서비스까지 내려가지 않고 입구에서 끊는다.
        verifyNoInteractions(authService);
    }

    @Test
    void 리프레시_쿠키가_빈문자열이면_UNAUTHORIZED() {
        assertThatThrownBy(() -> authController.refreshToken("   ", new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(authService);
    }

    @Test
    void 리프레시_쿠키가_있으면_재발급하고_쿠키를_굽는다() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authService.refreshToken(any())).thenReturn(new TokenRefreshResponse("newAccess", "newRefresh"));

        var result = authController.refreshToken("oldRefresh", response);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().accessToken()).isEqualTo("newAccess");
        verify(authCookieManager).writeTokens(response, "newAccess", "newRefresh");
    }
}
