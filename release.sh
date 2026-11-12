#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_FILE="$DIR/android-client/app/build.gradle.kts"
KEYSTORE_PROPS="$DIR/keystore.properties"

usage() {
    cat <<'EOF'
Использование:
  ./release.sh <HTTPS_API_BASE_URL> [GITHUB_OWNER/REPO] [RELEASE_TAG]

Переменные окружения (можно задать вместо аргументов):
  API_BASE_URL   Обязательный HTTPS-адрес бэкенда (например https://time-capsule-api.onrender.com)
  GITHUB_REPO    Репозиторий GitHub (например username/time-capsule-project)
  RELEASE_TAG    Тег GitHub Release (по умолчанию v<versionName>-c<versionCode>)

Что делает скрипт:
  1. Проверяет, что API_BASE_URL начинается с https:// и настроен keystore.properties
  2. Поднимает versionCode на +1 в android-client/app/build.gradle.kts
  3. Собирает подписанный release APK (./gradlew assembleRelease)
  4. Проверяет подпись утилитой apksigner verify --verbose --print-certs
  5. Вычисляет SHA-256 и выводит готовые значения LATEST_VERSION_CODE, LATEST_APK_URL, LATEST_APK_SHA256
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

API_BASE_URL="${1:-${API_BASE_URL:-}}"
GITHUB_REPO="${2:-${GITHUB_REPO:-}}"
CUSTOM_TAG="${3:-${RELEASE_TAG:-}}"

if [[ -z "$API_BASE_URL" ]]; then
    echo "Ошибка: укажите HTTPS-адрес сервера (первый аргумент или API_BASE_URL)." >&2
    usage >&2
    exit 1
fi

if [[ ! "$API_BASE_URL" =~ ^https://[^[:space:]]+$ ]]; then
    echo "Ошибка: API_BASE_URL должен начинаться с https:// и не содержать пробелов (получено: '$API_BASE_URL')." >&2
    exit 1
fi

if [[ ! -f "$KEYSTORE_PROPS" && ! -f "$DIR/android-client/keystore.properties" && ! -f "$DIR/android-client/app/keystore.properties" ]]; then
    echo "Ошибка: keystore.properties не найден. Подписанный релиз невозможен." >&2
    echo "Убедитесь, что release.keystore и keystore.properties существуют в корне проекта." >&2
    exit 1
fi

if [[ -z "${JAVA_HOME:-}" && -d "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" ]]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
fi
if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME/bin" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# Автоопределение GITHUB_REPO из git remote origin (если не передан явно)
if [[ -z "$GITHUB_REPO" ]]; then
    ORIGIN_URL="$(git -C "$DIR" remote get-url origin 2>/dev/null || true)"
    if [[ "$ORIGIN_URL" =~ github\.com[:/]([^/]+/[^/.]+)(\.git)?$ ]]; then
        GITHUB_REPO="${BASH_REMATCH[1]}"
    else
        GITHUB_REPO="OWNER/time-capsule-project"
    fi
fi

if [[ ! -f "$GRADLE_FILE" ]]; then
    echo "Ошибка: не найден файл $GRADLE_FILE" >&2
    exit 1
fi

OLD_VERSION_CODE="$(awk -F'=' '/^[[:space:]]*versionCode[[:space:]]*=/{gsub(/[^0-9]/,"",$2); print $2; exit}' "$GRADLE_FILE")"
VERSION_NAME="$(awk -F'"' '/^[[:space:]]*versionName[[:space:]]*=/{print $2; exit}' "$GRADLE_FILE")"

if [[ -z "$OLD_VERSION_CODE" || ! "$OLD_VERSION_CODE" =~ ^[0-9]+$ ]]; then
    echo "Ошибка: не удалось прочитать текущий versionCode из $GRADLE_FILE" >&2
    exit 1
fi

if [[ -z "$VERSION_NAME" ]]; then
    VERSION_NAME="1.0.0"
fi

NEW_VERSION_CODE=$((OLD_VERSION_CODE + 1))

TMP_GRADLE="$(mktemp)"
awk -v new_code="$NEW_VERSION_CODE" '
    BEGIN { updated = 0 }
    !updated && /^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*[0-9]+/ {
        sub(/versionCode[[:space:]]*=[[:space:]]*[0-9]+/, "versionCode = " new_code)
        updated = 1
    }
    { print }
' "$GRADLE_FILE" > "$TMP_GRADLE"
mv "$TMP_GRADLE" "$GRADLE_FILE"

echo "versionCode обновлён: $OLD_VERSION_CODE -> $NEW_VERSION_CODE (versionName=$VERSION_NAME)"
echo "Сборка подписанного release APK с -PapiBaseUrl=$API_BASE_URL ..."

(
    cd "$DIR/android-client"
    ./gradlew assembleRelease "-PapiBaseUrl=$API_BASE_URL" --no-daemon
)

RAW_APK="$DIR/android-client/app/build/outputs/apk/release/app-release.apk"
DIST_APK="$DIR/android-client/app/build/outputs/apk/release/timecapsule-release.apk"

if [[ ! -f "$RAW_APK" ]]; then
    echo "Ошибка: собранный APK не найден: $RAW_APK" >&2
    exit 1
fi

cp "$RAW_APK" "$DIST_APK"

# Поиск apksigner
APKSIGNER_BIN=""
for candidate in "$DIR"/android-client/.android-sdk/build-tools/*/apksigner; do
    if [[ -x "$candidate" ]]; then
        APKSIGNER_BIN="$candidate"
    fi
done
if [[ -z "$APKSIGNER_BIN" && -n "${ANDROID_HOME:-}" ]]; then
    for candidate in "$ANDROID_HOME"/build-tools/*/apksigner; do
        if [[ -x "$candidate" ]]; then
            APKSIGNER_BIN="$candidate"
        fi
    done
fi
if [[ -z "$APKSIGNER_BIN" ]]; then
    APKSIGNER_BIN="$(command -v apksigner || true)"
fi

if [[ -z "$APKSIGNER_BIN" || ! -x "$APKSIGNER_BIN" ]]; then
    echo "Ошибка: утилита apksigner не найдена в .android-sdk/build-tools или PATH." >&2
    exit 1
fi

echo ""
echo "=== Проверка подписи APK (apksigner verify) ==="
"$APKSIGNER_BIN" verify --verbose --print-certs "$DIST_APK"

# Вычисление SHA-256
if command -v shasum >/dev/null 2>&1; then
    APK_SHA256="$(shasum -a 256 "$DIST_APK" | awk '{print $1}')"
elif command -v sha256sum >/dev/null 2>&1; then
    APK_SHA256="$(sha256sum "$DIST_APK" | awk '{print $1}')"
else
    echo "Ошибка: не найден ни shasum, ни sha256sum." >&2
    exit 1
fi

TAG="${CUSTOM_TAG:-v${VERSION_NAME}-c${NEW_VERSION_CODE}}"
APK_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG}/timecapsule-release.apk"

echo ""
echo "================================================================"
echo " Релизный APK собран и проверен!"
echo " Файл для загрузки в GitHub Releases: $DIST_APK"
echo " Предлагаемый тег релиза: $TAG"
echo "================================================================"
echo "Скопируйте эти переменные окружения в настройки сервера (Render):"
echo ""
echo "LATEST_VERSION_CODE=$NEW_VERSION_CODE"
echo "LATEST_VERSION_NAME=$VERSION_NAME"
echo "LATEST_APK_URL=$APK_URL"
echo "LATEST_APK_SHA256=$APK_SHA256"
echo "LATEST_MIN_SUPPORTED_VERSION_CODE=1"
echo ""

