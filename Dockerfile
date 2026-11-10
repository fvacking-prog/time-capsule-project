# Multi-stage build for Time Capsule 2.0 Backend
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app
COPY backend ./backend
WORKDIR /app/backend
RUN ./gradlew fatJar --no-daemon

# Runtime container
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=builder /app/backend/build/libs/timecapsule-server.jar ./timecapsule-server.jar

ENV PORT=8080
ENV DEMO_MODE=true
ENV H2_URL="jdbc:h2:file:/app/data/timecapsule;DB_CLOSE_DELAY=-1"

VOLUME ["/app/data"]
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/timecapsule-server.jar"]
