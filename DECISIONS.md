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
