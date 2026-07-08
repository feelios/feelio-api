package com.korit.feelioapi.domain.auth.mapper;

import com.korit.feelioapi.domain.auth.entity.RefreshToken;
import com.korit.feelioapi.domain.auth.entity.SocialAccount;
import com.korit.feelioapi.domain.auth.entity.TermsAgreement;
import com.korit.feelioapi.domain.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * auth 도메인 데이터 접근 (순수 SQL — AuthMapper.xml 과 namespace/메서드명 일치).
 * 개인 데이터는 인증 주체 user_id 기준으로만 다룬다.
 */
@Mapper
public interface AuthMapper {

    /** (provider, provider_user_id) 로 소셜 계정 조회. 없으면 null → 신규가입. */
    SocialAccount findSocialAccountByProvider(@Param("provider") String provider,
                                              @Param("providerUserId") String providerUserId);

    /** 기존 회원 정보 조회(로그인 응답 조립용). */
    User findUserById(@Param("userId") Long userId);

    /** users insert. useGeneratedKeys 로 userId 를 채운다. */
    void insertUser(User user);

    void insertSocialAccount(SocialAccount socialAccount);

    /** notification_settings 는 컬럼 기본값에 의존 — user_id 만 넣는다. */
    void insertNotificationSettingDefault(@Param("userId") Long userId);

    void insertTermsAgreements(@Param("agreements") List<TermsAgreement> agreements);

    void insertRefreshToken(RefreshToken refreshToken);

    /** hash 기반 리프레시 토큰 단건 조회. */
    RefreshToken findRefreshTokenByHash(@Param("userId") Long userId, @Param("tokenHash") String tokenHash);

    /** 사용이 완료되거나 만료된 리프레시 토큰 삭제. */
    void deleteRefreshToken(@Param("tokenId") Long tokenId);
}
