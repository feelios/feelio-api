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
}
