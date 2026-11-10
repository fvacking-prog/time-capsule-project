#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"

ADB_BIN="$DIR/android-client/.android-sdk/platform-tools/adb"
if [ ! -f "$ADB_BIN" ]; then
    ADB_BIN="$(command -v adb || true)"
fi

if [ -z "$ADB_BIN" ] || [ ! -x "$ADB_BIN" ]; then
    echo "Ошибка: adb не найден в .android-sdk/platform-tools/adb или PATH"
    exit 1
fi

echo "=========================================="
echo " Time Capsule 2.0: Install & Run APK"
echo " ADB: $ADB_BIN"
echo "=========================================="

APK="$DIR/android-client/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
    echo "Сборка APK..."
    cd "$DIR/android-client"
    ./gradlew assembleDebug --no-daemon
fi

echo "Проверка подключённых устройств..."
DEVICES="$("$ADB_BIN" devices | grep -v "List" | grep "device$" || true)"
if [ -z "$DEVICES" ]; then
    echo "Предупреждение: не найдено запущенных эмуляторов или подключённых устройств."
    echo "Запустите эмулятор в Android Studio и повторите команду."
    exit 1
fi

echo "Установка APK ($APK)..."
"$ADB_BIN" install -r "$APK"

echo "Запуск приложения на устройстве..."
"$ADB_BIN" shell am start -n com.example.cyberpunk/.MainActivity

echo "Приложение Time Capsule 2.0 успешно запущено!"

