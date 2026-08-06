package com.korit.feelioapi.domain.user.mapper;

import com.korit.feelioapi.domain.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 사용자 데이터 접근 (순수 SQL — UserMapper.xml 과 namespace/메서드명 일치).
 * 모든 조회·수정은 user_id 기준.
 */
@Mapper
public interface UserMapper {

    User findUserById(@Param("userId") Long userId);

    /** user 객체의 provider 필드용 — 연동 소셜 계정 중 최신 1건. */
    String findProviderByUserId(@Param("userId") Long userId);

    int updateNickname(@Param("userId") Long userId, @Param("nickname") String nickname);

    int markOnboardingDone(@Param("userId") Long userId, @Param("totalAsset") Long totalAsset);

    /**
     * 거래가 자산에 준 변화량(수입 − 지출). 거래가 없으면 0.
     *
     * users.total_asset 은 온보딩 초기값 그대로 두고, 조회 시점에 이 값을 더해 현재 총자산을 만든다.
     * 컬럼을 직접 증감하면 생성·수정·삭제 중 한 경로만 빠져도 값이 영구히 틀어진다(#200).
     */
    long sumTransactionEffect(@Param("userId") Long userId);

    /** 부분 전송: null 인 컬럼은 건드리지 않는다(동적 SQL). */
    int updateSettings(@Param("userId") Long userId,
                       @Param("themeMode") String themeMode,
                       @Param("auroraTheme") String auroraTheme);

    int updateFcmToken(@Param("userId") Long userId, @Param("fcmToken") String fcmToken);

    // --- 회원탈퇴(A3-4) ---
    // users 는 soft(status=WITHDRAWN), 하위 데이터는 hard delete.
    // FK 미사용(참조 무결성=앱 레벨)이라 하위 테이블을 직접 지운다. terms_agreements 는 보존(법적 보관).
    int markWithdrawn(@Param("userId") Long userId);

    int deleteSocialAccountsByUserId(@Param("userId") Long userId);
    int deleteRefreshTokensByUserId(@Param("userId") Long userId);
    int deleteNotificationSettingsByUserId(@Param("userId") Long userId);
    int deleteTransactionsByUserId(@Param("userId") Long userId);
    int deleteGoalsByUserId(@Param("userId") Long userId);
    int deleteMonthlySummariesByUserId(@Param("userId") Long userId);
    int deleteAiInsightsByUserId(@Param("userId") Long userId);
}
