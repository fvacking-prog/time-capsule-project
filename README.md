# Time Capsule 2.0

Гео-синхронизированные капсулы времени с поэтапной разблокировкой (`AES-256-GCM`), проверкой совместного присутствия участников (`Haversine` + окно активности 60 секунд) и авторизацией через Google (`Credential Manager API` + `GoogleIdTokenVerifier` + Bearer-сессии).

---

## Структура проекта

- `backend/` — сервер на Kotlin 2.0.21 + Ktor 3.0.1 (Netty) + Exposed 0.56.0 + H2 в файловом режиме (`jdbc:h2:file:./data/timecapsule;DB_CLOSE_DELAY=-1`). Данные и сессии сохраняются между перезапусками сервера.
- `android-client/` — Android-клиент на Kotlin 2.0.21 + Jetpack Compose (Material 3, тема Tokyo Night) + Ktor Client 3.0.1 + Credential Manager API + DataStore + Coil. Включает 3 вкладки: «Радар», «Мои капсулы» (с гео-сортировкой и Pull-to-refresh) и «Создать».
- `AUDIT.md` — отчёт QA и аудита безопасности проекта.
- `CHANGELOG.md` — история версий и изменений проекта.

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
5. На шаге **Test users** (пока приложение в статусе *Testing*) обязательно добавьте Google-аккаунты всех тестировщиков/участников, которые будут входить с Android-устройства.

### 2. Получение SHA-1 отпечатка ключа подписи Android
Для привязки Android-приложения к Google Cloud нужен SHA-1 сертификата (для debug-сборки используется `~/.android/debug.keystore`). Выполните одну из команд:

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

1. **Клиент №1 — Android Client ID** (нужен Google Play Services для разрешения выдачи токена приложению):
   - **Application type**: `Android`
   - **Package name**: `com.example.cyberpunk`
   - **SHA-1 certificate fingerprint**: вставьте SHA-1, полученный на шаге 2.
   - Нажмите **Create**.

2. **Клиент №2 — Web Application Client ID** (`GOOGLE_WEB_CLIENT_ID`):
   - **Application type**: `Web application`
   - **Name**: `Time Capsule 2.0 Backend / Credential Manager`
   - Нажмите **Create** и скопируйте сгенерированный **Client ID** вида `1234567890-abcdefg.apps.googleusercontent.com`.
   - Именно **Web Client ID** передаётся в `GetGoogleIdOption.Builder().setServerClientId(...)` на Android и проверяется в `GoogleIdTokenVerifier` (`audience == GOOGLE_WEB_CLIENT_ID`) на бэкенде.

### 4. Прописывание `GOOGLE_WEB_CLIENT_ID` (без хардкода в репозитории)

1. **Android (`android-client/local.properties`)**:
   Добавьте ваш Web Client ID в файл `android-client/local.properties` (файл добавлен в `.gitignore`):
   ```properties
   sdk.dir=/Users/ghost/time-capsule-project/android-client/.android-sdk
   GOOGLE_WEB_CLIENT_ID=1234567890-abcdefg.apps.googleusercontent.com
   ```
   Скрипт `android-client/app/build.gradle.kts` автоматически пробросит значение в `BuildConfig.GOOGLE_WEB_CLIENT_ID`.

2. **Backend (переменная окружения `GOOGLE_WEB_CLIENT_ID`)**:
   При запуске бэкенда передайте переменную окружения:
   ```bash
   cd backend
   export GOOGLE_WEB_CLIENT_ID="1234567890-abcdefg.apps.googleusercontent.com"
   export DEMO_MODE="true"
   ./gradlew run
   ```

> **Запасной вариант для защиты проекта:** На экране входа также доступна секция **«Демо-вход (Maga / Zainab)»** (`POST /api/v1/auth/demo` при `DEMO_MODE=true`), которая позволяет войти и продемонстрировать работу капсулы времени даже без интернета или без настроенного Google-аккаунта на эмуляторе.

---

## API и авторизация

- `POST /api/v1/auth/google` — принимает `GoogleAuthRequest(idToken)`, проверяет подпись, срок действия, `audience == GOOGLE_WEB_CLIENT_ID`, `issuer` и `email_verified` через `GoogleIdTokenVerifier`, создаёт/обновляет пользователя в таблице `Users`, создаёт сессию на 30 дней в таблице `Sessions` и возвращает `AuthResponse(user, sessionToken)`.
- `POST /api/v1/auth/demo` — демо-вход для `user_maga` / `user_zainab` (работает только при `DEMO_MODE=true`).
- `GET /api/v1/me` — возвращает `UserDto` текущего авторизованного пользователя.
- `GET /api/v1/users?query=` — поиск зарегистрированных пользователей по имени или email для приглашения в капсулу (чужие email маскируются в формате `m***a@gmail.com`).
- `POST /api/v1/capsules/join` — принимает `JoinCapsuleRequest(inviteCode)`. Проверяет существование капсулы по 6-символьному коду приглашения, добавляет авторизованного пользователя в список участников `CapsuleParticipants`, автоматически переводит капсулу в групповой режим `isGroupCapsule = true` и возвращает обновлённый `CapsuleDto`.
- `POST /api/v1/capsules/{id}/open` — фиксирует факт открытия капсулы (`openedAt = now`) текущим пользователем. Возвращает `409 Conflict`, если капсула ещё не готова к открытию.
- `POST /api/v1/capsules/{id}/archive` — перемещает капсулу в архив текущего пользователя (`archived = true`).
- `POST /api/v1/capsules/{id}/unarchive` — возвращает капсулу из архива (`archived = false`).
- `DELETE /api/v1/me/data` — полное удаление всех данных пользователя (созданные капсулы, участие в чужих капсулах, сессии).
- `GET /api/v1/capsules` — возвращает список капсул времени. Каждая капсула содержит поле `creatorId`, `inviteCode`, вычисляемый статус для текущего пользователя (`status: SEALED | READY | OPENED | ARCHIVED`), признак гео-привязки `isGeoLocked`, адресата `recipient`, теги `tags` и тип `kind`.
- Все эндпоинты `/api/v1/capsules/*` требуют заголовок `Authorization: Bearer <sessionToken>`. Без токена или с неверным/просроченным токеном сервер возвращает `401 Unauthorized`. Личность создателя капсулы и участника при `check-in` определяется строго по Bearer-токену.

---

## Возможности приложения Time Capsule 2.0

1. **Раздел «Главная» (`HomeScreen`)**: персональное приветствие, крупная карточка быстрого запечатывания нового воспоминания, блок «Ближайшее открытие» с динамическим таймером обратного отсчёта до секунды, поле ввода кода приглашения и быстрый вход в радар.
2. **Раздел «Капсулы» («Полка воспоминаний», `CapsulesShelfScreen`)**: поиск капсул по названию, типу, адресату и #тегам, разделение по 4 вкладкам статуса («Запечатанные», «Готовые», «Открытые», «Архив»), Pull-to-refresh, действие открытия для готовых капсул и меню архивации.
3. **Раздел «Создать» (`CreateCapsuleScreen`)**: пошаговый мастер создания воспоминаний (Текст / Фото «Скоро» / Аудио «Скоро»), текст до 5000 символов, адресат, теги, чипы выбора срока (Завтра, Неделя, Месяц, Календарь, Демо), карточка предпросмотра, предупреждение о неизменяемости запечатанной капсулы и переключатель режима гео-локации.
4. **Раздел «Идеи» (`IdeasScreen`)**: готовые шаблоны и вдохновение для капсул («Письмо себе через 10 лет», «Прогнозы на 2036 год», «Послание будущему ребёнку», «Годовщина», «День рождения», «Семейная история») с мгновенным переносом в форму создания.
5. **Раздел «Настройки» (`SettingsScreen`)**: управление профилем, выход из аккаунта, переключение сервера и демо-режима, защита приложения 4-значным PIN-кодом с хэшированием (salt + SHA-256), безопасный JSON-экспорт данных (`Intent.ACTION_SEND`), удаление всех данных (`DELETE /api/v1/me/data`) и информационные диалоги.
6. **Экран «Радар»**: радарная сетка, неоновая 3D-модель капсулы с динамической скоростью вращения, отслеживание GPS-позиции и автоматический периодический heartbeat check-in.
7. **Хранилище данных**: постоянное хранение базы данных H2 в файле `./data/timecapsule.mv.db` на стороне бэкенда, сохраняющее данные между перезапусками сервера.
8. **Эффекты открытия капсулы**: виброотклик (`HapticFeedback`) и неоновый всплеск при переходе капсулы в разблокированное состояние.
9. **Таймлайн с подсветкой текущего этапа**: экран «Письмо в будущее по пунктам» с вертикальной шкалой времени, визуальной подсветкой текущего активного этапа, датами открытия и расшифровкой секретного текста (`AES-256-GCM`).

