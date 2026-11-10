#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEYSTORE_PATH="$DIR/release.keystore"
PROPS_PATH="$DIR/keystore.properties"

echo "========================================================"
echo " Генерация релизного хранилища ключей (Release Keystore)"
echo " для приложения «Time Capsule 2.0»"
echo "========================================================"

if [ -f "$KEYSTORE_PATH" ]; then
    echo "Хранилище ключей уже существует: $KEYSTORE_PATH"
    if [ -n "$AUTO_KEYSTORE_PASSWORD" ]; then
        echo "Используется существующий ключ."
    else
        read -p "Перезаписать существующий ключ? (y/N): " CONFIRM
        if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
            echo "Отмена."
            exit 0
        fi
        rm -f "$KEYSTORE_PATH"
    fi
fi

KEY_ALIAS="timecapsule"

if [ -n "$AUTO_KEYSTORE_PASSWORD" ]; then
    STORE_PASS="$AUTO_KEYSTORE_PASSWORD"
    KEY_PASS="$AUTO_KEYSTORE_PASSWORD"
else
    echo ""
    read -s -p "Введите пароль для хранилища ключей (минимум 6 символов): " STORE_PASS
    echo ""
    read -s -p "Повторите пароль: " STORE_PASS_CONFIRM
    echo ""
    if [ "$STORE_PASS" != "$STORE_PASS_CONFIRM" ]; then
        echo "Ошибка: пароли не совпадают!"
        exit 1
    fi
    KEY_PASS="$STORE_PASS"
fi

if [ ${#STORE_PASS} -lt 6 ]; then
    echo "Ошибка: пароль должен содержать не менее 6 символов!"
    exit 1
fi

KEYTOOL_BIN="keytool"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    KEYTOOL_BIN="$JAVA_HOME/bin/keytool"
elif [ -x "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/keytool" ]; then
    KEYTOOL_BIN="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/keytool"
fi

if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "Генерация RSA 2048 ключа (срок действия 10000 дней)..."
    "$KEYTOOL_BIN" -genkeypair -v \
        -keystore "$KEYSTORE_PATH" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "$STORE_PASS" \
        -keypass "$KEY_PASS" \
        -dname "CN=Time Capsule Team, OU=Release, O=Time Capsule, L=Khujand, C=TJ"
fi

cat <<EOF > "$PROPS_PATH"
storeFile=release.keystore
storePassword=$STORE_PASS
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASS
EOF

echo "✓ Хранилище: $KEYSTORE_PATH"
echo "✓ Настройки подписи сохранены в: $PROPS_PATH"
echo ""
echo "=== SHA-1 отпечаток для Google Cloud Console ==="
"$KEYTOOL_BIN" -list -v -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" -storepass "$STORE_PASS" | grep "SHA1:" || true
echo "==============================================="
