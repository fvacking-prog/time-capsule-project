#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"

if [ -f "$DIR/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    source "$DIR/.env"
    set +a
fi

if [ -z "${CAPSULE_AES_KEY:-}" ]; then
    echo "ОШИБКА: Переменная окружения CAPSULE_AES_KEY не задана."
    echo "Скопируйте .env.example в .env и сгенерируйте ключ: ./generate-aes-key.sh"
    exit 1
fi

echo "=========================================="
echo " Starting Time Capsule 2.0 Backend"
echo " JAVA_HOME: $JAVA_HOME"
echo " Host:      http://0.0.0.0:${PORT:-8080}"
echo "=========================================="

BINARY="$DIR/backend/build/install/time-capsule-backend/bin/time-capsule-backend"
if [ -f "$BINARY" ]; then
    exec "$BINARY"
else
    cd "$DIR/backend"
    exec ./gradlew run
fi

