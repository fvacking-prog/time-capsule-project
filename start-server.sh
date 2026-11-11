#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

if [ -f "$DIR/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    source "$DIR/.env"
    set +a
fi

PORT="${PORT:-8080}"
export CAPSULE_AES_KEY="${CAPSULE_AES_KEY:-}"
export GOOGLE_WEB_CLIENT_ID="${GOOGLE_WEB_CLIENT_ID:-}"

if [ -z "$CAPSULE_AES_KEY" ]; then
    echo "ОШИБКА: Переменная CAPSULE_AES_KEY не задана."
    echo "Создайте .env из .env.example и сгенерируйте ключ командой: ./generate-aes-key.sh"
    exit 1
fi

echo "========================================================"
echo " Запуск автономного сервера «Time Capsule 2.0»"
echo "========================================================"
echo " Порт:        $PORT"
echo " Java:        $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
echo "========================================================"

# Проверка занятости порта
PORT_BUSY=0
if command -v lsof >/dev/null 2>&1; then
    if lsof -Pi :"$PORT" -sTCP:LISTEN -t >/dev/null 2>&1; then
        PORT_BUSY=1
    fi
elif command -v nc >/dev/null 2>&1; then
    if nc -z 127.0.0.1 "$PORT" >/dev/null 2>&1; then
        PORT_BUSY=1
    fi
fi

if [ "$PORT_BUSY" -eq 1 ]; then
    echo ""
    echo "ОШИБКА: Порт $PORT уже занят другим процессом!"
    echo "Остановите работающий сервер (например, через ./reset-data.sh или kill) или задайте другой порт: PORT=8081 ./start-server.sh"
    exit 1
fi

JAR_FILE="$DIR/backend/build/libs/timecapsule-server.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Fat JAR не найден, выполняется быстрая сборка fatJar..."
    cd "$DIR/backend"
    ./gradlew fatJar --no-daemon
    cd "$DIR"
fi

echo "Сервер запускается из $JAR_FILE ..."
exec "$JAVA_HOME/bin/java" -jar "$JAR_FILE"

