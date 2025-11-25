# Build stage
FROM gradle:8.5-jdk21-alpine AS build
WORKDIR /app

# Copy gradle files first for better caching
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds)
RUN ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar
COPY docker-entrypoint.sh ./docker-entrypoint.sh
RUN chmod +x docker-entrypoint.sh

# Set default port (Railway will provide PORT via environment)
ENV PORT=8080

# Use custom entrypoint to normalize database variables before boot
ENTRYPOINT ["/app/docker-entrypoint.sh"]
