#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
export DEMO_MODE="${DEMO_MODE:-true}"

echo "=========================================="
echo " Starting Time Capsule 2.0 Backend"
echo " JAVA_HOME: $JAVA_HOME"
echo " DEMO_MODE: $DEMO_MODE"
echo " Host:      http://0.0.0.0:8080"
echo "=========================================="

BINARY="$DIR/backend/build/install/time-capsule-backend/bin/time-capsule-backend"
if [ -f "$BINARY" ]; then
    exec "$BINARY"
else
    cd "$DIR/backend"
    exec ./gradlew run
fi

