# syntax=docker/dockerfile:1

# =============================================================================
# Build stage — Gradle wrapper로 bootJar 생성 (테스트 skip)
# =============================================================================
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# 의존성 레이어 캐시 활용: build 파일 먼저 복사
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --no-watch-fs || true

# 소스 복사 후 빌드
COPY src src
RUN ./gradlew bootJar -x test --no-daemon --no-watch-fs

# =============================================================================
# Runtime stage — JRE slim + non-root + KST 시간대
# =============================================================================
FROM eclipse-temurin:21-jre-jammy

ENV TZ=Asia/Seoul

RUN useradd --create-home --shell /bin/bash spring
USER spring
WORKDIR /home/spring

COPY --from=build /workspace/build/libs/*.jar /home/spring/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/spring/app.jar"]
