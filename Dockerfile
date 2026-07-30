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

COPY --from=builder /app/build/libs/*.jar app.jar

# root 로 실행하지 않는다
RUN useradd -r -u 1001 appuser && chown appuser:appuser /app/app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
