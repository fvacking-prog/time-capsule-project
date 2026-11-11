# Time Capsule 2.0

Гео-синхронизированные капсулы времени с поэтапной разблокировкой (`AES-256-GCM`), строгим правилом открытия (`now >= unlockDate` и для гео-капсул — присутствие всех участников в радиусе с `lastSeenAt` не старше 60 секунд) и авторизацией через Google (`Credential Manager API` + `GoogleIdTokenVerifier` + Bearer-сессии).

---

## Структура проекта

- `backend/` — сервер на Kotlin 2.0.21 + Ktor 3.0.1 (Netty) + Exposed 0.56.0 + H2 в файловом режиме (`jdbc:h2:file:./data/timecapsule;DB_CLOSE_DELAY=-1`). Собирается в автономный Fat JAR (`timecapsule-server.jar`).
- `android-client/` — Android-клиент на Kotlin 2.0.21 + Jetpack Compose (Material 3, тема Tokyo Night) + Ktor Client 3.0.1 + Credential Manager API + DataStore + Coil. Включает 5 разделов навигации («Главная», «Капсулы», «Создать», «Идеи», «Настройки»), экраны «Радар» и «Таймлайн».
- `RELEASE.md` — релизное руководство: конфигурация `CAPSULE_AES_KEY`, сборка APK/AAB с `-PapiBaseUrl=https://...`, подпись, привязка SHA-1 в Google Cloud Console, диагностика R8 и пошаговый сценарий проверки.
- `.env.example` — шаблон переменных окружения бэкенда (`PORT`, `H2_URL`, `CAPSULE_AES_KEY`, `GOOGLE_WEB_CLIENT_ID`).
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

- `POST /api/v1/auth/google` — принимает `GoogleAuthRequest(idToken)`, проверяет подпись, срок действия, `audience == GOOGLE_WEB_CLIENT_ID`, `issuer` и `email_verified` через `GoogleIdTokenVerifier`, создаёт/обновляет пользователя в таблице `Users`, создаёт сессию на 30 дней в таблице `Sessions` и возвращает `AuthResponse(user, sessionToken)`.
- `GET /api/v1/me` — возвращает `UserDto` текущего авторизованного пользователя.
- `GET /api/v1/users?query=` — поиск зарегистрированных пользователей по имени или email для приглашения в капсулу (чужие email маскируются в формате `m***a@gmail.com`; `googleSub` и чужие raw-email не возвращаются).
- `POST /api/v1/capsules` — создание личной или гео-капсулы с шифрованием этапов AES-256-GCM. Дата каждого этапа должна быть в будущем (минимум +1 минута).
- `POST /api/v1/capsules/{id}/check-in` — фиксирует GPS-координаты и `lastSeenAt = now` авторизованного участника.
- `POST /api/v1/capsules/join` — принимает `JoinCapsuleRequest(inviteCode)` и добавляет авторизованного пользователя в `CapsuleParticipants`.
- `POST /api/v1/capsules/{id}/open` — фиксирует `openedAt = now` после выполнения всех условий (`now >= unlockDate`, а для гео-капсул ещё и нахождение всех участников в радиусе с `lastSeenAt` не старше 60 с). Возвращает `409 Conflict`, если условия ещё не выполнены.
- `POST /api/v1/capsules/{id}/archive` / `unarchive` — управление архивом текущего пользователя.
- `DELETE /api/v1/me/data` — полное удаление данных пользователя (созданные капсулы, участие в чужих капсулах, сессии).
- `GET /api/v1/capsules` — возвращает список капсул текущего пользователя.
- Все эндпоинты `/api/v1/capsules/*`, `/api/v1/users`, `/api/v1/me*` требуют заголовок `Authorization: Bearer <sessionToken>`.

---

## Возможности приложения Time Capsule 2.0

1. **Раздел «Главная» (`HomeScreen`)**: персональное приветствие, быстрый переход к созданию нового воспоминания, блок «Ближайшее открытие» с обратным отсчётом, ввод кода приглашения и вход в радар.
2. **Раздел «Капсулы» (`CapsulesShelfScreen`)**: поиск по названию, типу, адресату и #тегам, 4 вкладки статуса («Запечатанные», «Готовые», «Открытые», «Архив»), Pull-to-refresh, открытие и архивация.
3. **Раздел «Создать» (`CreateCapsuleScreen`)**: пошаговый мастер создания с `DatePicker` и `TimePicker` (минимум +1 минута от текущего времени для удобного тестирования), карточкой предпросмотра (`dd.MM.yyyy HH:mm`) и переключателем режима гео-локации.
4. **Раздел «Идеи» (`IdeasScreen`)**: шаблоны воспоминаний с переносом в форму создания.
5. **Раздел «Настройки» (`SettingsScreen`)**: профиль, выход, 4-значный PIN-код (`salt + SHA-256`), экспорт JSON, удаление данных (`DELETE /api/v1/me/data`); в `debug`-сборках также доступна ручная настройка URL сервера.
6. **Экран «Радар» (`RadarScreen`)**: радарная сетка, 3D-модель капсулы, отслеживание GPS-позиции и автоматический heartbeat check-in каждые 15 с.
7. **Экран «Таймлайн» (`CapsuleStagesTimeline`)**: этапы послания с датой/временем и расшифровкой секретного текста (`AES-256-GCM`).
