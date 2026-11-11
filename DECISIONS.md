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
