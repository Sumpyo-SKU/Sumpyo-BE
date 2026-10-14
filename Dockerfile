# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 캐시: 빌드 스크립트만 먼저 복사해서 소스 변경 시 의존성 재다운로드 방지
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Seoul

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
