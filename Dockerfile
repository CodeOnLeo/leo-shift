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

# Create volume directory for H2 database
RUN mkdir -p /app/data

# Copy the built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Set default port (Railway will provide PORT via environment)
ENV PORT=8080

# Set environment variable for database location
ENV SPRING_DATASOURCE_URL=jdbc:h2:file:/app/data/leoshift;MODE=PostgreSQL

# Run the application with proper memory settings
CMD java -Xmx512m -Xms256m -Dserver.port=$PORT -jar /app/app.jar
