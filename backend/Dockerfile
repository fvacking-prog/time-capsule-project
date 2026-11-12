# Multi-stage Dockerfile for Time Capsule 2.0 Backend (Render & Docker Compose)
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
COPY src ./src
RUN ./gradlew fatJar --no-daemon

# Production runtime on JRE 21
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appuser \
    && useradd -r -g appuser -m -d /app appuser

WORKDIR /app
COPY --from=builder /app/build/libs/timecapsule-server.jar ./timecapsule-server.jar
RUN mkdir -p /app/data && chown -R appuser:appuser /app

USER appuser

ENV PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD sh -c "curl -fsS http://127.0.0.1:\${PORT:-8080}/health || exit 1"

ENTRYPOINT ["java", "-jar", "/app/timecapsule-server.jar"]

