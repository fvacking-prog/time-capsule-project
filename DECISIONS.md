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
