# Релизная документация «Time Capsule 2.0»

Руководство по конфигурированию шифрования, сборке, подписи, проверке и развёртыванию сервера и Android-клиента.

---

## 1. Артефакты сборки

| Артефакт | Путь к файлу | Описание |
|---|---|---|
| **Debug APK** | `android-client/app/build/outputs/apk/debug/app-debug.apk` | Отладочная сборка (`http://10.0.2.2:8080` по умолчанию, доступен выбор URL сервера и cleartext в `src/debug`). |
| **Release APK** | `android-client/app/build/outputs/apk/release/app-release.apk` | Релизная сборка с R8, сжатием ресурсов, только HTTPS и обязательным `-PapiBaseUrl=https://...`. |
| **Release AAB** | `android-client/app/build/outputs/bundle/release/app-release.aab` | Android App Bundle для публикации в Google Play Console. |
| **Standalone Server JAR** | `backend/build/libs/timecapsule-server.jar` | Автономный Fat JAR сервер (Netty + Exposed + H2). |

---

## 2. Конфигурация бэкенда (`CAPSULE_AES_KEY`)

Бэкенд шифрует содержимое капсул алгоритмом **AES-256-GCM**. Переменная окружения `CAPSULE_AES_KEY` обязательна всегда и должна содержать ровно 32 байта в Base64; при её отсутствии или неверном формате сервер не запускается и выводит диагностическое сообщение.

1. Сгенерируйте случайный ключ:
   ```bash
   ./generate-aes-key.sh
   ```
2. Создайте `.env` из `.env.example`:
   ```bash
   cp .env.example .env
   ./generate-aes-key.sh --env >> .env
   ```
3. Запустите сервер или Docker:
   ```bash
   ./start-server.sh
   # или
   CAPSULE_AES_KEY="$(./generate-aes-key.sh)" docker compose up -d --build
   ```

> **Правило разблокировки капсулы:** этап и сама капсула переходят в состояние `READY` (`isFullyUnlocked = true`) только при одновременном выполнении условий: `now >= unlockDate` И (для гео-капсул `isGeoLocked = true`) все участники находятся в пределах `radiusMeters` с отметкой `lastSeenAt` не старше 60 секунд.

---

## 3. Сборка и подпись Android-клиента

### 3.1. Генерация ключа подписи (Keystore)
```bash
./make-keystore.sh
```
Пример `keystore.properties` (добавлен в `.gitignore`):
```properties
storeFile=release.keystore
storePassword=ВашПароль
keyAlias=timecapsule
keyPassword=ВашПароль
```
*(Если `keystore.properties` отсутствует, Gradle соберёт `release` неподписанным с предупреждением в консоли.)*

### 3.2. Обязательный HTTPS-адрес `-PapiBaseUrl` для Release
Параметр `-PapiBaseUrl` задаёт `BuildConfig.API_BASE_URL`:
- Для **Release (`assembleRelease` / `bundleRelease`)** параметр `-PapiBaseUrl` обязателен и должен начинаться с `https://`. При отсутствии или указании `http://` сборка немедленно завершается с `GradleException`.
- Для **Debug (`assembleDebug`)** при отсутствии `-PapiBaseUrl` подставляется `http://10.0.2.2:8080`; ручная смена адреса в UI и `network_security_config.xml` с cleartext включены исключительно в `src/debug`.

### 3.3. Команды сборки и тестов
Backend (JDK 17 или 21):
```bash
cd backend
./gradlew test build fatJar --no-daemon
```
*(Тестовая конфигурация в `backend/build.gradle.kts` автоматически задаёт тестовый `CAPSULE_AES_KEY`.)*

Android (JDK 17 или 21):
```bash
cd android-client
./gradlew lint testReleaseUnitTest assembleDebug assembleRelease -PapiBaseUrl=https://api.example.com --no-daemon
```

---

## 4. Регистрация Release SHA-1 и диагностика Google-входа

### 4.1. Регистрация Release SHA-1 в Google Cloud Console
1. Получите SHA-1 отпечаток вашего ключа:
   ```bash
   keytool -list -v -keystore release.keystore -alias timecapsule
   ```
2. В [Google Cloud Console](https://console.cloud.google.com/) → **APIs & Services** → **Credentials** создайте **Android OAuth Client ID** для `applicationId = com.example.cyberpunk` и этого SHA-1.
3. Убедитесь, что **Web Client ID** (`GOOGLE_WEB_CLIENT_ID`) задан в `android-client/local.properties` и в окружении бэкенда. Без `GOOGLE_WEB_CLIENT_ID` релизная сборка завершится с ошибкой `GradleException`.

### 4.2. Диагностика ошибок Google-входа (Credential Manager)

| Ошибка / Код | Причина | Способ решения |
|---|---|---|
| **DEVELOPER_ERROR (код 10)** | Несовпадение SHA-1 отпечатка или пакета в Google Cloud Console. | Проверьте SHA-1 релизного (или debug) ключа и пакет `com.example.cyberpunk`. |
| **Нет подключения к сети (код 7)** | Отсутствует интернет на устройстве. | Проверьте сеть и доступность HTTPS-бэкенда. |
| **Вход отменён пользователем** | Пользователь закрыл системный диалог выбора Google-аккаунта. | Повторите попытку входа. |
| **Не найден аккаунт Google** | На устройстве или эмуляторе не добавлен Google-аккаунт. | Добавьте Google-аккаунт в настройках Android. |
| **Недействительный токен (401)** | Токен истёк или не совпадает `GOOGLE_WEB_CLIENT_ID` на клиенте и сервере. | Сверьте `GOOGLE_WEB_CLIENT_ID` и повторите вход. |

### 4.3. Конфликт подписей (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)
Если ранее стояла `debug`-версия, перед установкой `release`-APK удалите старую сборку:
```bash
adb uninstall com.example.cyberpunk
adb install android-client/app/build/outputs/apk/release/app-release.apk
```

---

## 5. Проверка без эмулятора vs на реальном устройстве

- Для быстрого тестирования временной блокировки в разделе **«Создать»** используйте кнопки выбора даты и **выбора времени (`TimePicker`)**: минимально допустимое время открытия — **+1 минута** от текущего момента.
- Сетевые политики релиза гарантируются конфигурацией [network_security_config.xml](file:///Users/ghost/time-capsule-project/android-client/app/src/main/res/xml/network_security_config.xml) (`cleartextTrafficPermitted="false"`) и манифестом [AndroidManifest.xml](file:///Users/ghost/time-capsule-project/android-client/app/src/main/AndroidManifest.xml) (`android:usesCleartextTraffic="false"`), тогда как cleartext для эмулятора/LAN изолирован в `android-client/app/src/debug/`.
