package com.korit.feelioapi.global.ai;

/**
 * 서킷이 열려 있어 모델 호출을 아예 시도하지 않았음을 알린다 (#197).
 *
 * <p>각 생성기의 {@code catch (Exception e)} 가 이걸 받아 규칙기반 문구로 넘어간다.
 * 호출 실패와 같은 갈래로 흘러가되, 네트워크를 타지 않아 즉시 떨어진다는 점만 다르다.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }
}
