package com.korit.feelioapi.domain.summary.service;

import com.korit.feelioapi.domain.analysis.service.SpendStatus;

/**
 * 말랑이 코멘트 문장 생성 교체 지점. 개인 식별 정보는 전달하지 않는다.
 *
 * <p>상태 판정(SpendStatus)과 수치는 이미 서버가 계산해 넘긴다.
 * 생성기는 그 값을 문장으로 옮길 뿐이며 숫자를 바꾸지 않는다.
 *
 * <p>실패하면 null 을 반환한다. 폴백은 서비스가 붙인다.
 */
public interface MallangCommentGenerator {
    MallangComment generate(SpendStatus status, long expense, long budget, int usageRate);
}
