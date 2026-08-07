package com.korit.feelioapi.global.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 모델 호출 앞에 두는 서킷 브레이커 (#197).
 *
 * <p>생성기마다 호출 단위 타임아웃과 규칙기반 폴백은 이미 있었다. 없던 건 <b>실패가 이어질 때
 * 시도조차 하지 않는 장치</b>다. OpenAI 가 죽어 있으면 요청마다 타임아웃을 꽉 채우고서야
 * 폴백으로 갔고, AI 리포트 한 화면이 생성기를 셋 부르므로 그 대기가 겹쳐 쌓였다.
 *
 * <p>연속 실패가 기준치를 넘으면 일정 시간 서킷을 열어 {@link AiUnavailableException} 을
 * 즉시 던진다. 각 생성기의 {@code catch} 가 그대로 받아 규칙기반 문구로 넘어가므로,
 * 호출부는 손댈 게 없고 사용자는 기다리지 않는다. 열린 시간이 지나면 다시 한 번 시도해 보고,
 * 또 실패하면 곧바로 다시 닫는다(half-open).
 *
 * <p>여러 요청이 동시에 열린 시간의 끝을 넘길 수 있다. 그때 탐색 호출 몇 개가 같이 나가는 건
 * 막지 않았다 — 그걸 막으려면 락이 필요한데, 이 규모에서 얻는 것보다 잃는 게 크다.
 */
@Component
public class AiCallGuard {

    private static final Logger log = LoggerFactory.getLogger(AiCallGuard.class);

    private final int failureThreshold;
    private final Duration openDuration;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.EPOCH);

    public AiCallGuard(@Value("${feelio.ai.circuit.failure-threshold:3}") int failureThreshold,
                       @Value("${feelio.ai.circuit.open-seconds:60}") long openSeconds) {
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofSeconds(openSeconds);
    }

    /**
     * 서킷이 닫혀 있을 때만 {@code action} 을 실행한다.
     *
     * @param callName 로그에서 어느 생성기인지 알아보려는 용도
     * @throws AiUnavailableException 서킷이 열려 있어 호출하지 않은 경우
     */
    public <T> T call(String callName, Supplier<T> action) {
        if (Instant.now().isBefore(openUntil.get())) {
            throw new AiUnavailableException(
                    String.format("AI 서킷이 열려 있어 %s 호출을 건너뛴다", callName));
        }

        try {
            T result = action.get();
            recordSuccess();
            return result;
        } catch (RuntimeException e) {
            recordFailure(callName);
            throw e;
        }
    }

    private void recordSuccess() {
        // 한 번이라도 성공하면 원점으로. 간헐적 실패로 서킷이 열리지 않게 한다.
        if (consecutiveFailures.getAndSet(0) > 0) {
            openUntil.set(Instant.EPOCH);
            log.info("AI 호출이 다시 성공했다. 서킷을 닫는다.");
        }
    }

    private void recordFailure(String callName) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            openUntil.set(Instant.now().plus(openDuration));
            log.warn("AI 호출이 {}회 연속 실패({}). {}초 동안 호출을 멈추고 규칙기반으로 답한다.",
                    failures, callName, openDuration.toSeconds());
        }
    }
}
