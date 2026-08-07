FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew

RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

# 프로필은 런타임에 환경변수로 결정한다.
# ARG 는 빌드 타임 전용이라 실행 시점에 존재하지 않고,
# ENTRYPOINT 의 exec 형태(JSON 배열)는 셸을 안 거쳐 ${} 치환도 되지 않는다.
# SPRING_PROFILES_ACTIVE 는 Spring 이 직접 읽으므로 치환이 필요 없다.
#   docker run -e SPRING_PROFILES_ACTIVE=dev ... 로 덮어쓸 수 있다.
ENV SPRING_PROFILES_ACTIVE=prod

# 서비스 시간대는 한국이다.
# occurredAt 은 계약 §6 대로 오프셋 없는 로컬 시각(한국 벽시계)으로 오간다.
# 컨테이너가 UTC 로 돌면 서버의 '지금'이 9시간 뒤처져, 방금 한 기록도
# @PastOrPresent 검증에서 미래로 판정돼 거부된다(#283).
# LocalDateTime.now()·월별 집계·최근 7일 챌린지도 모두 이 시계를 따르므로 함께 고정한다.
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /app/build/libs/*.jar app.jar

# root 로 실행하지 않는다
RUN useradd -r -u 1001 appuser && chown appuser:appuser /app/app.jar
USER appuser

EXPOSE 8080

# JVM 기본 시간대도 못박는다. 이미지에 tzdata 가 없거나 /etc/localtime 이 비어도
# 서버의 '지금'이 UTC 로 떨어지지 않게 하기 위함이다.
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app/app.jar"]
