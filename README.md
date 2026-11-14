# Time Capsule 2.0

Гео-синхронизированные капсулы времени с поэтапной разблокировкой (`AES-256-GCM`), строгим правилом открытия (`now >= unlockDate` и для гео-капсул — присутствие всех участников в радиусе с `lastSeenAt` не старше 60 секунд) и авторизацией через Google (`Credential Manager API` + `GoogleIdTokenVerifier` + Bearer-сессии).

---

## Структура проекта

- `backend/` — сервер на Kotlin 2.0.21 + Ktor 3.0.1 (Netty) + Exposed 0.56.0 + HikariCP 6.0.0 (`PostgreSQL` через `DATABASE_URL` от Neon или файловая `H2` при отсутствии `DATABASE_URL`). Собирается в автономный Fat JAR (`timecapsule-server.jar`) и multi-stage Docker-образ (`backend/Dockerfile`).
- `android-client/` — Android-клиент на Kotlin 2.0.21 + Jetpack Compose (Material 3, тема Tokyo Night) + Ktor Client 3.0.1 + Credential Manager API + DataStore + Coil + WorkManager. Включает 5 разделов навигации («Главная», «Капсулы» со вкладкой «Входящие» и бейджем, «Создать», «Идеи», «Настройки»), экраны «Выбор @handle», «Радар» и «Таймлайн».
- `docs/index.html` — веб-страница загрузки APK для GitHub Pages в стиле Tokyo Night со ссылкой на последний GitHub Release.
- `release.sh` — скрипт инкремента `versionCode`, сборки подписанного release APK, проверки подписи через `apksigner` и вывода `LATEST_*` переменных.
- `RELEASE.md` — релизное руководство: конфигурация `CAPSULE_AES_KEY`, PostgreSQL/Docker, сборка APK/AAB с `-PapiBaseUrl=https://...`, подпись, привязка SHA-1 в Google Cloud Console и публикация обновлений.
- `.env.example` — шаблон переменных окружения бэкенда (`PORT`, `DATABASE_URL`, `H2_URL`, `CAPSULE_AES_KEY`, `GOOGLE_WEB_CLIENT_ID`, `LATEST_*`).
- `generate-aes-key.sh` — генератор случайного 32-байтного ключа AES-256 в формате Base64 для `CAPSULE_AES_KEY`.

---

## Быстрый старт и запуск

1. **Настройка ключа шифрования и запуск сервера (автономный Fat JAR):**
   `CAPSULE_AES_KEY` обязателен всегда (строго 32 байта в Base64). При его отсутствии или неверной длине сервер завершает старт с ошибкой.
   ```bash
   cp .env.example .env
   ./generate-aes-key.sh --env >> .env
   ./start-server.sh
   ```
   Сервер стартует на `http://0.0.0.0:8080`.
   Для запуска JAR напрямую с явными переменными окружения:
   ```bash
   export CAPSULE_AES_KEY="$(./generate-aes-key.sh)"
   PORT=8080 java -jar backend/build/libs/timecapsule-server.jar
   ```
   Для сборки и тестирования бэкенда (тестовый ключ задан в `backend/build.gradle.kts`):
   ```bash
   cd backend
   ./gradlew test build fatJar
   ```
   Для сброса базы используйте `./reset-data.sh`. Доступен также запуск через Docker: `CAPSULE_AES_KEY="$(./generate-aes-key.sh)" docker compose up -d --build`.

2. **Сборка Android-клиента (Debug / Staging / Release):**
   Адрес сервера задаётся через `-PapiBaseUrl` (`BuildConfig.API_BASE_URL`):
   - В `debug` по умолчанию используется `http://10.0.2.2:8080` (cleartext и ручная смена адреса доступны только в `debug` через `src/debug/res/xml/network_security_config.xml`).
   - В `release` параметр `-PapiBaseUrl` обязателен и обязан использовать `https://`, иначе сборка завершится ошибкой.
   ```bash
   # 1. Генерация ключа подписи (если ещё не создан):
   ./make-keystore.sh

   # 2. Сборка и проверки:
   cd android-client
   ./gradlew lint testReleaseUnitTest assembleDebug
   ./gradlew assembleRelease -PapiBaseUrl=https://api.example.com
   ./gradlew bundleRelease -PapiBaseUrl=https://api.example.com
   ```
   Пути к артефактам:
   - Debug APK: `android-client/app/build/outputs/apk/debug/app-debug.apk`
   - Release APK: `android-client/app/build/outputs/apk/release/app-release.apk`
   - Release AAB: `android-client/app/build/outputs/bundle/release/app-release.aab`
   - Server JAR: `backend/build/libs/timecapsule-server.jar`

---

## Настройка входа через Google (Google Cloud Console)

Для работы кнопки **«Войти через Google»** через `Credential Manager API` (`GetGoogleIdOption` + `GoogleIdTokenCredential`) выполните следующие шаги в [Google Cloud Console](https://console.cloud.google.com/):

### 1. Создание проекта и настройка OAuth Consent Screen
1. Откройте **APIs & Services → OAuth consent screen**.
2. Выберите тип пользователей **External** и нажмите **Create**.
3. Заполните обязательные поля:
   - **App name**: `Time Capsule 2.0`
   - **User support email** и **Developer contact information**: ваш email.
4. На шаге **Scopes** добавьте стандартные области: `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile`.
5. На шаге **Test users** (пока приложение в статусе *Testing*) добавьте Google-аккаунты тестировщиков.

### 2. Получение SHA-1 отпечатка ключа подписи Android
Для привязки Android-приложения к Google Cloud нужен SHA-1 сертификата:

Через Gradle:
```bash
cd android-client
./gradlew signingReport
```
Или напрямую через `keytool`:
```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```
Скопируйте значение строки `SHA1: XX:XX:XX:...`.

### 3. Создание двух OAuth 2.0 Client ID (Android + Web)
Перейдите в **APIs & Services → Credentials → Create Credentials → OAuth client ID**:

1. **Клиент №1 — Android Client ID**:
   - **Application type**: `Android`
   - **Package name**: `com.example.cyberpunk`
   - **SHA-1 certificate fingerprint**: вставьте SHA-1, полученный на шаге 2.

2. **Клиент №2 — Web Application Client ID** (`GOOGLE_WEB_CLIENT_ID`):
   - **Application type**: `Web application`
   - Скопируйте **Client ID** вида `1234567890-abcdefg.apps.googleusercontent.com`.
   - Именно Web Client ID передаётся в `GetGoogleIdOption.Builder().setServerClientId(...)` на Android и проверяется в `GoogleIdTokenVerifier` (`audience == GOOGLE_WEB_CLIENT_ID`) на бэкенде.

### 4. Прописывание `GOOGLE_WEB_CLIENT_ID` (без хардкода в репозитории)
1. **Android (`android-client/local.properties`)**:
   ```properties
   sdk.dir=/Users/ghost/time-capsule-project/android-client/.android-sdk
   GOOGLE_WEB_CLIENT_ID=1234567890-abcdefg.apps.googleusercontent.com
   ```
2. **Backend (`.env` или переменная окружения `GOOGLE_WEB_CLIENT_ID`)**:
   ```bash
   cd backend
   export GOOGLE_WEB_CLIENT_ID="1234567890-abcdefg.apps.googleusercontent.com"
   export CAPSULE_AES_KEY="$(../generate-aes-key.sh)"
   ./gradlew run
   ```

---

## API и авторизация

- `GET /ping` — легковесная проверка доступности сервера (`200 ok` без обращения к БД; позволяет Uptime-монитору будить Render, не препятствуя спящему режиму Neon PostgreSQL).
- `GET /health` — проверка состояния сервера и соединения с БД (`200 ok` или `503 database_unavailable`).
- `GET /api/v1/app/version` — публичный эндпоинт (без Bearer) актуальной версии Android-клиента (`versionCode`, `versionName`, `apkUrl` только `https://`, `apkSha256`, `changelog`, `minSupportedVersionCode`).
- `POST /api/v1/auth/google` — принимает `GoogleAuthRequest(idToken)`, проверяет подпись, срок действия, `audience == GOOGLE_WEB_CLIENT_ID`, `issuer` и `email_verified` через кешируемый синглтон `GoogleIdTokenVerifier`, создаёт/обновляет пользователя в таблице `Users`, создаёт сессию на 30 дней в таблице `Sessions` и возвращает `AuthResponse(user, sessionToken)`.
- `GET /api/v1/me` — возвращает `UserDto` текущего авторизованного пользователя.
- `GET /api/v1/users/check-handle?handle=` и `PUT /api/v1/users/me/handle` — проверка формата/занятости/зарезервированных и сохранение уникального `@handle` (`^[a-z0-9_]{4,24}$`; при конфликте возвращает `409 Conflict`).
- `GET /api/v1/users?query=` — SQL-поиск до 30 пользователей (только с заданным `handle`, без самого себя и без заблокированных в любую сторону) по префиксу `@handle` и имени (`email` и `googleSub` для чужих пользователей никогда не возвращаются).
- `POST /api/v1/capsules` — создание личной, по-`@handle` (до 10 получателей при `isGeoLocked = false`, лимит 30 отправленных капсул/сутки -> `429`) или гео-капсулы (до 5 этапов) с шифрованием текста и медиа (фото до 1 МБ, голос до 60 с / 1 МБ) алгоритмом AES-256-GCM (поддерживается бесшовная ротация ключа через `CAPSULE_AES_KEY_PREVIOUS`).
- `POST /api/v1/capsules/{id}/check-in` — фиксирует GPS-координаты и `lastSeenAt = now` авторизованного участника.
- `POST /api/v1/capsules/join` — принимает `JoinCapsuleRequest(inviteCode)` и добавляет авторизованного пользователя в групповую капсулу (`403 Forbidden` для личных капсул и при блокировках).
- `POST /api/v1/capsules/{id}/open` — фиксирует `openedAt = now` после выполнения условий открытия (`hasUnlockedStage == true`). Открытая один раз гео-капсула (`openedAt != null`) остаётся открытой для участника навсегда.
- `POST /api/v1/capsules/{id}/archive` / `unarchive` — управление архивом текущего пользователя.
- `POST /api/v1/blocks` / `DELETE /api/v1/blocks/{userId}` / `GET /api/v1/blocks` — двусторонняя блокировка пользователей.
- `DELETE /api/v1/me/data` — полное и безвозвратное удаление всех данных пользователя (прикреплённые медиафайлы, этапы, созданные капсулы, участие в чужих капсулах, блокировки, сессии и строка профиля в таблице `Users`).
- `GET /api/v1/capsules` — батч-загрузка списка капсул текущего пользователя (не более 6 SQL-запросов, метаданные медиа без чтения BLOB `encrypted_bytes`).
- Все эндпоинты `/api/v1/capsules/*`, `/api/v1/users/*`, `/api/v1/blocks*`, `/api/v1/me*` требуют заголовок `Authorization: Bearer <sessionToken>`.

---

## Возможности приложения Time Capsule 2.0

1. **Раздел «Главная» (`HomeScreen`)**: персональное приветствие, быстрый переход к созданию нового воспоминания, блок «Ближайшее открытие» с посекундным обратным отсчётом (`ДД:ЧЧ:ММ:СС`) и градиентной полосой «выдержки» капсулы, переход в «Мою статистику», ввод кода приглашения и вход в радар.
2. **Раздел «Капсулы» (`CapsulesShelfScreen`)**: поиск по названию, типу, адресату и `#тегам`, 5 вкладок («Все», «Входящие» с бейджем, «Запечатанные», «Готовые», «Открытые/Архив»), Pull-to-refresh, открытие и архивация.
3. **Раздел «Создать» (`CreateCapsuleScreen`)**: пошаговый мастер создания с поддержкой многоэтапных капсул (до 5 этапов с проверкой хронологического порядка дат), вложений фото и голоса, выбора получателей по `@handle` и режима гео-локации.
4. **Раздел «Идеи» (`IdeasScreen`)**: шаблоны воспоминаний (включая «Фото-архив» и «Голосовое в 2026») с переносом в форму создания.
5. **Раздел «Настройки» (`SettingsScreen`)**: профиль и `@handle`, локальные уведомления 2.0 (3 канала Android, `AlarmManager`, тихие часы 23:00–08:00, годовщина через `WorkManager`), полноэкранная защита 4-значным PIN-кодом (`PBKDF2WithHmacSHA256`, 60 000 итераций, кулдаун 30 с, `FLAG_SECURE`) и вход по отпечатку пальца (`BiometricPrompt`), экспорт JSON, полное удаление профиля и данных (`DELETE /api/v1/me/data`), встроенная проверка обновлений APK с проверкой `SHA-256`.
6. **Экран «Моя статистика» (`StatsScreen`)**: аналитика на Compose Canvas (KPI, серия месяцев активности, тепловая карта за 6 месяцев, столбцы по месяцам, кольцо топ-5 тегов и линия будущих открытий).
7. **Экран «Радар» (`RadarScreen`)**: радарная сетка, 3D-модель капсулы (`Capsule3DView`), отслеживание GPS-позиции и автоматический heartbeat check-in.
8. **Экран «Таймлайн» (`CapsuleStagesTimeline`)**: этапы послания с расшифровкой секретного текста (`AES-256-GCM`), просмотром фото и голосовым плеером с перемоткой, а также экспортом открытой капсулы в неоновую PNG-карточку (без текста письма) или полный PDF-документ через системный Share Sheet (`FileProvider`).
