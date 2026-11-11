#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${PORT:-8080}"

echo "========================================================"
echo " Сброс базы данных «Time Capsule 2.0»"
echo "========================================================"

if [ "$AUTO_CONFIRM" != "1" ]; then
    echo "ВНИМАНИЕ: Это действие остановит работающий сервер и полностью удалит базу данных ($DIR/data)."
    read -p "Вы уверены, что хотите продолжить? (y/N): " CONFIRM
    if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
        echo "Сброс отменён."
        exit 0
    fi
fi

echo "Остановка процессов сервера на порту $PORT..."
if command -v lsof >/dev/null 2>&1; then
    PIDS=$(lsof -Pi :"$PORT" -sTCP:LISTEN -t 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
        echo "Завершение процессов: $PIDS"
        kill -9 $PIDS 2>/dev/null || true
    fi
fi

pkill -f "timecapsule-server" 2>/dev/null || true
pkill -f "time-capsule-backend" 2>/dev/null || true

echo "Удаление директории базы данных ./data..."
rm -rf "$DIR/data"
rm -f "$DIR"/*.mv.db

echo ""
echo "✓ База данных успешно очищена."
echo "✓ При следующем старте сервера (./start-server.sh) схема базы данных будет создана заново."

