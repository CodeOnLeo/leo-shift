# Build stage
FROM gradle:8.5-jdk21-alpine AS build
WORKDIR /app

# 의존성 캐시를 위해 빌드 파일을 먼저 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

COPY src ./src

RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 컨테이너 기본 시간대. 설정하지 않으면 UTC라 모든 시각이 9시간 어긋난다.
ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata

COPY --from=build /app/build/libs/*.jar app.jar

ENV PORT=8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=$PORT -jar /app/app.jar"]
