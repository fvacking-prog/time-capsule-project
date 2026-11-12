# MORNING_REPORT.md

Итоговый отчёт автономной доработки проекта **Time Capsule 2.0** (ветка `production`).

---

## 1. Статус фаз

| Фаза | Статус | Коммит | Что сделано и что не выполнено |
|---|---|---|---|
| **Фаза A** (Предусловие) | **Выполнено** | `b9b3e35` | Удалены `/api/v1/auth/demo`, `/demo-force-checkin`, `seedDemoData()`, `DEMO_MODE`, `user_maga`/`user_zainab`, переключение Demo Mode, кнопки «Демо-вход» и чипы `[ДЕМО]`, `BuildConfig.ENABLE_DEMO`, `MockCapsuleRepository`. `CAPSULE_AES_KEY` (32 байта Base64) обязателен при старте сервера; `API_BASE_URL` в `release` строго HTTPS; в «Создать» добавлен `TimePicker` (+1 мин); исправлено правило `.gitignore` (`data/` -> `/data/`) для отслеживания Android-пакета `data`. |
| **Фаза 2** (Google-вход и `@handle`) | **Выполнено** *(устройство — см. блокеры)* | `afb3ccd` | Добавлена колонка `Users.handle` (nullable, уникальный lowercase-индекс, `^[a-z0-9_]{4,24}$`, зарезервированы `admin`, `support`, `root`, `system`, `timecapsule`), `UserDto(handle, avatarUrl)`, эндпоинты `GET /api/v1/users/check-handle`, `PUT /api/v1/users/me/handle`, расширенный `GET /api/v1/users?query=` (префикс `@handle` и имя, только с `handle`, без себя, до 20 результатов, чужие `email` и `googleSub` никогда не отдаются). `POST /api/v1/auth/google` принимает только `idToken` и проверяет `GoogleIdTokenVerifier`. В Android реализован 2-шаговый Credential Manager (`filterByAuthorizedAccounts = true` -> `false`), диагностика отмены/сети/отсутствия аккаунта/`DEVELOPER_ERROR`, обязательный `GOOGLE_WEB_CLIENT_ID` для release, экран `ChooseHandleScreen` (дебаунс 400 мс), `@handle` и аватар (`Coil`) в профиле. Написаны тесты. Не проверялось на физическом устройстве с Google Play Services. |
| **Фаза 3** (Капсулы по `@handle`, Входящие, Блокировки) | **Выполнено** | `d84d93f` | Добавлены `CapsuleParticipants.role` (`CREATOR` / `RECIPIENT` / `MEMBER`, по умолчанию `MEMBER`) и таблица `Blocks`. Поддержана отправка не-гео капсулы (`isGeoLocked = false`) до 10 получателям по `@handle` (пусто = себе). На клиенте: вкладка «Входящие» с бейджем, `@handle` отправителя, сокрытие зашифрованного текста до даты, статусы «Доставлено» / «Открыто получателем» (`openedAt`), суточный лимит 30 отправленных капсул (`429`), «Заблокировать отправителя», локальные уведомления через `WorkManager`, обновление на старте/возврате и pull-to-refresh. Покрыто тестами видимости, шифрования, лимита и блокировки. |
| **Фаза 4** (PostgreSQL + Docker + Cold-start retry) | **Выполнено частично** *(нет Docker-демона)* | `7d8bf40` | Реализован `DatabaseConfig`: разбор Neon `DATABASE_URL` (`postgresql://...` -> `jdbc:postgresql://...` с отдельными `username`/`password`), fallback на файловую H2 без `DATABASE_URL`, зависимости `postgresql:42.7.4` и `HikariCP:6.0.0` (`maxPoolSize = 3`, `connectionTimeout = 10 s`), `SchemaUtils.createMissingTablesAndColumns`, проверка БД в `GET /health` (`200 ok` / `503 database_unavailable`), корректное закрытие пула при остановке. Созданы multi-stage `backend/Dockerfile` (fatJar -> JRE 21, `PORT`, `0.0.0.0`, не-root, `HEALTHCHECK`) и `docker-compose.dev.yml`. На клиенте добавлен `ColdStartRetry` (до 3 повторов с паузами `3 -> 6 -> 12 с`) и баннер «Сервер просыпается, подождите до минуты». **Не выполнено:** `docker build` и интеграционный тест на живом контейнере PostgreSQL (не запущен Docker Desktop). |
| **Фаза 5** (Интернет-раздача и автообновление APK) | **Выполнено** *(устройство — см. блокеры)* | `9e77680` | Реализован публичный `GET /api/v1/app/version` (без Bearer, переменные `LATEST_*`, строгий фильтр `https://` для `apkUrl`). В Android: проверка при старте (таймаут 5 с, ошибки молча), кнопка «Проверить обновления» в Настройках, диалог обновления в стиле Tokyo Night (обязательный при версии ниже `minSupportedVersionCode`), загрузка в `cacheDir/apk-updates/` с прогрессом, **проверка SHA-256 перед установкой** с удалением файла при несовпадении, `FileProvider` (узкий путь `apk-updates/`), `ACTION_VIEW`, переход в `ACTION_MANAGE_UNKNOWN_APP_SOURCES` с пояснением и `REQUEST_INSTALL_PACKAGES` в манифесте. Созданы `docs/index.html` (Tokyo Night для GitHub Pages) и скрипт `release.sh`. Проверка реальной установки APK на телефоне невозможна без устройства. |
| **Итог** (Полная проверка и отчёты) | **Выполнено** | текущий коммит | Проведены финальные чистые проверки backend, Android, `diff` DTO, статики и подписей APK; созданы `MORNING_REPORT.md` и `MORNING_CHECKLIST.md`. |

---

## 2. Коммиты в ветке `production`

- `b9b3e35` — `Phase A: remove demo mode, enforce CAPSULE_AES_KEY and https apiBaseUrl, track Android data package`
- `afb3ccd` — `Phase 2: Google Sign-In, @handle onboarding, handle checks, user search, privacy & tests`
- `d84d93f` — `Phase 3: @handle recipients, Inbox tab & badge, blocks, 30/day limit, WorkManager notifications & tests`
- `7d8bf40` — `Phase 4: PostgreSQL (Neon/HikariCP), Docker multi-stage build, DB health check & Android cold-start retry`
- `9e77680` — `Phase 5: /api/v1/app/version, in-app SHA-256 APK updater, docs/index.html & release.sh`

---

## 3. Результаты финальных проверок

1. **Backend (`clean test build fatJar`)**:
   ```bash
   JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
     ./backend/gradlew -p backend clean test build fatJar --no-daemon
   ```
   - Результат: **BUILD SUCCESSFUL in 56s** (код выхода `0`, `11 actionable tasks: 11 executed`).
   - Отчёт тестов (`BackendUnitTests`): **26 тестов, 0 пропущено, 0 падений, 0 ошибок**.
   - Артефакт: `backend/build/libs/timecapsule-server.jar` (`26M`).

2. **Android (`clean lint testReleaseUnitTest assembleDebug assembleRelease`)**:
   ```bash
   ./android-client/gradlew -p android-client clean lint testReleaseUnitTest assembleDebug assembleRelease \
     -PapiBaseUrl=https://api.timecapsule.example.com --no-daemon
   ```
   - Результат: **BUILD SUCCESSFUL in 4m 17s** (код выхода `0`, `104 actionable tasks: 102 executed`).
   - Отчёт юнит-тестов: `DtoSerializationTest` (**4 теста**) + `UnlockAndConfigUnitTest` (**10 тестов**) = **14 тестов, 0 пропущено, 0 падений, 0 ошибок**.
   - Android Lint: без ошибок (`app/build/reports/lint-results-debug.html`).
   - Артефакты: `app-debug.apk` (`64M`), `app-release.apk` (`11M`).

3. **Подписи APK (`apksigner verify --print-certs`)**:
   - `app-release.apk`: валидна (`CN=Time Capsule Team, OU=Release, O=Time Capsule, L=Khujand, C=TJ`), **SHA-1: `A3:D1:F2:0F:A9:E3:DD:9D:DD:5A:5F:8A:5E:C6:A0:26:D6:84:9A:46`**.
   - `app-debug.apk`: валидна (`C=US, O=Android, CN=Android Debug`), **SHA-1: `37:CA:0F:A2:49:70:75:95:26:B8:65:6A:41:E8:AA:51:3C:38:C6:C0`**.

4. **Контракты, скрипты, Compose и отсутствие demo**:
   - `diff -u backend/.../ModelsDto.kt android-client/.../ModelsDto.kt`: код `0` (файлы идентичны).
   - `grep -rnEi 'demo|демо'` по `backend/src`, `android-client/app/src`, `docs`, `README.md`, `RELEASE.md`, `.env.example`: совпадений нет (`CLEAN_ZERO_DEMO_REFERENCES`).
   - `bash -n release.sh` и `bash -n generate-aes-key.sh`: код `0`.
   - `docker compose -f docker-compose.dev.yml config -q` (с тестовым `CAPSULE_AES_KEY`): код `0`.

---

## 4. Содержимое `BLOCKERS.md`

```markdown
# BLOCKERS.md

Список шагов или проверок, требующих окружения/действий на стороне человека.

## 1. Docker / Docker Compose (Фаза 4)
- **Что не выполнено**: Сборка Docker-образа из `backend/Dockerfile` (`docker build`), запуск `docker compose -f docker-compose.dev.yml up` и интеграционный прогон на живом контейнере PostgreSQL.
- **Почему**: Утилита `docker` установлена (`/usr/local/bin/docker`, v29.9.2), но Docker Desktop демон на этой машине не запущен (`Cannot connect to the Docker daemon at unix:///Users/ghost/.docker/run/docker.sock`).
- **Что сделать человеку**:
  1. Запустить приложение Docker Desktop на Mac.
  2. Проверить локально или сразу развернуть на Render (см. `MORNING_CHECKLIST.md`) и при желании выполнить:
     ```bash
     export CAPSULE_AES_KEY=$(./generate-aes-key.sh)
     docker compose -f docker-compose.dev.yml up --build
     ```

## 2. Физическое устройство / Android-эмулятор
- **Что не выполнено**: Живое прохождение на экране телефона: реальный Google Credential Manager (требует Google Play Services и SHA-1 в Google Cloud), GPS-чекин на местности, установка APK поверх текущей версии через системный установщик (`FileProvider` + `REQUEST_INSTALL_PACKAGES`).
- **Почему**: Автономная сборка без подключённого устройства/запущенного эмулятора и до настройки Google Cloud / Render / GitHub Releases.
- **Что сделать человеку**: Пройти простые шаги из `MORNING_CHECKLIST.md` и установить собранный релизный APK на Android-устройство.
```

---

## 5. Содержимое `DECISIONS.md`

```markdown
# DECISIONS.md

Автономные архитектурные и инженерные решения, принятые в процессе доработки проекта Time Capsule.

## Фаза A (Предусловие и гигиена репозитория)
1. **Исправление `.gitignore` для исходников Android (`data/` -> `/data/`)**:
   - Незаякоренное правило `data/` в корневом `.gitignore` игнорировало пакет исходного кода `android-client/app/src/main/java/com/example/cyberpunk/data/`, из-за чего `ApiConfig.kt`, `CapsuleRepository.kt`, `GeoHelper.kt`, `LocationProvider.kt`, `UnlockDateTimeHelper.kt`, `UserPreferences.kt` не отслеживались Git.
   - Принято решение заменить `data/` на `/data/` и `/backend/data/` (плюс `*.trace.db`, `*.h2.db`), чтобы каталоги локальной БД оставались вне Git, а Android-пакет `data` коммитился в ветку `production`.
2. **Полное сокрытие чужих email в API (`includeEmail = false` -> `null`)**:
   - Вместо частичного маскирования (`a***e@gmail.com`) для чужих пользователей поле `UserDto.email` всегда возвращается как `null`, а поиск `GET /api/v1/users` не фильтрует по чужому `email`. Это полностью исключает утечку чужих email через API.
3. **Неперезаписываемые секреты и исторические документы**:
   - Файлы `release.keystore`, `keystore.properties`, `android-client/local.properties`, `.env` не модифицируются и остаются в `.gitignore`.
   - Исторические отчёты `CHANGELOG.md`, `REPORT.md`, `AUDIT.md`, `SMOKE_TEST.md` не переписываются как лог прошлых версий; актуальная документация находится в `README.md`, `RELEASE.md`, `backend/README.md`, `DECISIONS.md`, `MORNING_REPORT.md`, `MORNING_CHECKLIST.md`.

## Фаза 2 (Google-вход и @никнеймы)
1. **Авторизация для проверки доступности никнейма (`GET /api/v1/users/check-handle`)**:
   - Эндпоинт защищён Bearer-сессией (`requireAuthenticatedUserId()`), поскольку на клиенте выбор `@handle` происходит сразу после успешного Google Sign-In. Это исключает анонимный перебор существующих аккаунтов.
2. **Обработка зарезервированных никнеймов (`admin`, `support`, `root`, `system`, `timecapsule`)**:
   - `GET /api/v1/users/check-handle` возвращает `valid=false`, `available=false`, `reason="RESERVED"`, а `PUT /api/v1/users/me/handle` отклоняет такие значения кодом `400 Bad Request`. На клиенте такие значения помечаются статусом «зарезервирован (занят)».
3. **Инжектируемая обёртка проверки Google ID-токена для тестов**:
   - В продакшене `POST /api/v1/auth/google` использует реальный `GoogleIdTokenVerifier` (RS256, срок, `aud`, `iss`, `email_verified`), а в юнит-тестах также тестируется с реальным `GoogleIdTokenVerifier` на невалидном токене (`401 Unauthorized`).

## Фаза 3 (Отправка капсул людям по @handle)
1. **Суточный лимит отправки (30/сутки)**:
   - Ограничение «не более 30 отправленных капсул в сутки на пользователя (429)» применяется к капсулам, созданным за последние 24 часа и адресованным другим участникам/получателям (`Capsules innerJoin CapsuleParticipants` с `userId != creatorId`). Создание личных капсул для себя («пусто = себе») не расходует квоту отправки другим людям.
2. **Бейдж новых входящих и вкладка «Входящие»**:
   - Новыми входящими считаются неархивные входящие капсулы (`isInbox == true`) с `openedAt == null` (ещё не открытые получателем). После вызова `POST /api/v1/capsules/{id}/open` счётчик бейджа уменьшается автоматически.
3. **Обратная совместимость ролей участников (`CapsuleParticipants.role`)**:
   - Новая колонка `role` имеет значение по умолчанию `MEMBER` для существующих записей в БД, при создании новых капсул создатель получает роль `CREATOR`, получатели не-гео капсул — `RECIPIENT`, участники гео-капсул — `MEMBER`.
4. **Двойная передача получателей (`recipientHandles` + `participantIds`)**:
   - В `CreateCapsuleRequest` добавлено опциональное поле `recipientHandles: List<String> = emptyList()`, что позволяет отправлять капсулы напрямую по `@handle` (и корректно объединяется с выбранными `participantIds`, ограничиваясь максимум 10 получателями для `isGeoLocked = false`).

## Фаза 4 (Постоянный сервер: PostgreSQL + HikariCP + Docker)
1. **Обработка `DATABASE_URL` от Neon и fallback на H2**:
   - Поддерживаются строки формата `postgresql://user:pass@host/db?sslmode=require`, `postgres://...` и готовые `jdbc:postgresql://...`; учетные данные декодируются через `URLDecoder` и передаются в `HikariConfig` отдельными `username`/`password` без включения пароля в JDBC URL. При пустой `DATABASE_URL` используется локальная файловая БД H2.
2. **Политика повторов при холодном старте (`ColdStartRetry`)**:
   - Клиент повторяет запрос до 3 раз с нарастающей паузой (`3 с -> 6 с -> 12 с`, таймаут попытки `15 с`) только при транзиентных сбоях пробуждения хостинга (`SocketTimeoutException`, `ConnectTimeoutException`, `HttpRequestTimeoutException`, `ConnectException`, HTTP `502/503/504`) и отображает баннер/сообщение «Сервер просыпается, подождите до минуты». Ошибки `4xx` (включая `401`) не повторяются.
3. **Проверка PostgreSQL без запущенного Docker-демона**:
   - Поскольку Docker Desktop на машине не запущен (зафиксировано в `BLOCKERS.md`), конфигурация `docker-compose.dev.yml`, multi-stage `backend/Dockerfile` (fatJar -> JRE 21, non-root, `HEALTHCHECK`), разбор `DATABASE_URL`, настройки пула `HikariCP` (`maxPoolSize=3`, `connectionTimeout=10_000`) и жизненный цикл `/health` проверены модульными/HTTP-тестами на H2.

## Фаза 5 (Установка из интернета и автообновление внутри приложения)
1. **Публичный эндпоинт `/api/v1/app/version` без Bearer и строгая валидация артефакта**:
   - Эндпоинт отдаёт поля из переменных `LATEST_*` без авторизации, чтобы обязательные обновления (`currentVersionCode < minSupportedVersionCode`) работали до входа пользователя.
   - На сервере и клиенте `apkUrl` проходит проверку `https://` без пробелов (иначе сервер отдаёт `""`), а `apkSha256` нормализуется к нижнему регистру 64-символьного hex. Переносы `\\n` в `LATEST_CHANGELOG` преобразуются в реальные переводы строк для удобного ввода в UI Render.
2. **Узкий путь `FileProvider`, проверка `SHA-256` и редиректы только по `HTTPS`**:
   - В `res/xml/file_paths.xml` открыт только подкаталог `<cache-path name="apk_updates" path="apk-updates/" />`.
   - Загрузка выполняется через `HttpsURLConnection` с ручной проверкой каждого `3xx`-редиректа (GitHub Releases CDN) на `https://`. При несовпадении `SHA-256` скачанный файл немедленно удаляется, а пользователю выводится ошибка без падения приложения.
3. **Устойчивость к отсутствию сети при старте**:
   - Стартовая проверка версии использует отдельный клиент с таймаутом `5 с` и полностью подавляет ошибки, не вызывая цикл повторов `ColdStartRetry`. Ручная проверка в Настройках явно сообщает, что версия актуальна или сервер недоступен.
4. **Автоопределение репозитория на странице `docs/index.html` и в `release.sh`**:
   - Страница `docs/index.html` использует цвета палитры Tokyo Night; при открытии на `*.github.io/<repo>` автоматически подставляет ссылку на `/releases/latest/download/timecapsule-release.apk`.
   - Скрипт `release.sh` перед изменением `versionCode` проверяет `https://` адрес и наличие `keystore.properties`, не затрагивая существующие `release.keystore` и `keystore.properties`.
```

