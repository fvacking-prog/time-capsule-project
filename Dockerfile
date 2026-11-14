# Multi-stage Dockerfile for Time Capsule 2.0 Backend (Root build context)
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app
COPY backend ./backend
WORKDIR /app/backend
RUN chmod +x ./gradlew && ./gradlew fatJar --no-daemon

# Production runtime on JRE 21
FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appuser \
    && useradd -r -g appuser -m -d /app appuser

WORKDIR /app
COPY --from=builder /app/backend/build/libs/timecapsule-server.jar ./timecapsule-server.jar
RUN mkdir -p /app/data && chown -R appuser:appuser /app

USER appuser

ENV PORT=8080
ENV H2_URL="jdbc:h2:file:/app/data/timecapsule;DB_CLOSE_DELAY=-1"
# CAPSULE_AES_KEY обязателен при запуске контейнера (32 байта в Base64)

VOLUME ["/app/data"]
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD sh -c "curl -fsS http://127.0.0.1:\${PORT:-8080}/health || exit 1"

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/timecapsule-server.jar"]

