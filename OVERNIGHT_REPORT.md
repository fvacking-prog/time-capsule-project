# OVERNIGHT_REPORT.md — Отчёт об автономной ночной доработке Time Capsule 2.0

**Дата выполнения:** 14.11.2026  
**Рабочая ветка:** `overnight` (создана от `production`, точка старта помечена тегом `before-overnight`)  
**Статус фаз:** **R1 — DONE (`r1-done`)**, **R2 — DONE (`r2-done`)**, **R3 — DONE (`r3-done`)**, **FINAL — DONE**  
**Блокеры (`BLOCKERS.md`):** 0 критических блокеров (все фазы пройдены с первой итерации без откатов).

---

## 1. Сводная таблица выполненных пунктов (R1–R3)

| Пункт | Описание задачи (по PLAN.md) | Статус | Коммит / Тег |
|---|---|---|---|
| **R1.1 (Аудит 1, 8)** | Список капсул не читает BLOB `encrypted_bytes` (проекция только `id`, `kind`, `mime`, `sizeBytes`, `durationMs`); батч-загрузка через `inList` (не более 6 SQL-запросов на весь список) | DONE | `8ad0c54` |
| **R1.2 (Аудит 2, 12)** | Если у участника `openedAt != null`, гео-условие выполнено навсегда (текст и медиа); поле `hasUnlockedStage` в `CapsuleDto` и клиенте; `isFullyUnlocked` = все этапы открыты | DONE | `c4f2249` |
| **R1.3 (Аудит 5)** | `DELETE /api/v1/me/data` удаляет строку пользователя в таблице `Users` и медиафайлы | DONE | `6a6cc7a` |
| **R1.4 (Аудит 6)** | `inviteCode` только для групповых капсул; `join` только в групповые (`403` для личных); поиск строго по `trim().uppercase()` без `selectAll()`; двусторонняя проверка блокировок | DONE | `6cbe1ff` |
| **R1.5 (Аудит 7)** | `AuthRateLimiter`: ключ = последнее значение `X-Forwarded-For` (или `remoteHost`), очистка при росте > 5000 ключей, защита `/capsules/join`, `/users/me/handle` и загрузки медиа | DONE | `a2e1195` |
| **R1.6 (Аудит 11)** | Ограничение чтения загрузок по байтам (`readNBytes(max + 1)`), защита от chunked-обхода `Content-Length` | DONE | `7d4d539` |
| **R1.7 (Аудит 9, 10)** | 7 индексов БД + явный `CREATE INDEX IF NOT EXISTS` при старте; поиск пользователей по `@handle` через SQL `LIKE` + `LIMIT 30` с исключением заблокированных | DONE | `6f0e409` |
| **R1.8 (Аудит 15)** | Бесшовная ротация ключа без смены формата шифротекста (`CAPSULE_AES_KEY_PREVIOUS` при `AEADBadTagException`); повреждённые записи не роняют весь список | DONE | `f595847` |
| **R1.9 (Аудит 16, 20, 28, 31, 34, 35 + `/ping`)** | Кешируемый синглтон `GoogleIdTokenVerifier`; Hikari `minimumIdle=0`, `idleTimeout=60000`, очистка сессий при старте и раз в 6 ч; `409` при дубле `@handle`; `CallLogging` и стектрейсы в `StatusPages` без заголовков `Authorization`; обновление `google-api-client` и `postgresql`; публичный `GET /ping`; унификация корневого `Dockerfile` | DONE | `8e444cc` |
| **R1.10 (Аудит 4, 14, 17, 19, 29)** | Клиент: убрана двойная навигация с полки; `retryTrigger` в `PhotoMediaCard`/`VoiceMediaCard` и перемотка `Slider` (`seekTo`); убран повторный `authGoogle` в `catch`; бейдж кода только у групповых; разблокированы шаблоны «Фото-архив» и «Голосовое в 2026», удалён мёртвый код | DONE | `f33a8fc` |
| **R1.Verify** | Обязательные тесты Фазы R1, сборка backend + android, проверка `diff ModelsDto.kt`, curl-сценарий на `fatJar` (порт 18080) | DONE | `46e4892` (`r1-done`) |
| **R2.1 (Аудит 3, Функция 2)** | `POST_NOTIFICATIONS` (API 33+), 3 канала уведомлений, `AlarmManager` (`canScheduleExactAlarms()` + fallback `setWindow`), `BootCompletedReceiver`, годовщина через `WorkManager`, тихие часы 23:00–08:00, статус разрешения в настройках, обработка `EXTRA_CAPSULE_ID` в `MainActivity` | DONE | `bf38eea` |
| **R2.2 (Аудит 13)** | Шифрование `session_token` через Android Keystore (AES/GCM) с миграцией; PIN `PBKDF2WithHmacSHA256` (60 000 итераций) с миграцией; кулдаун 30 с после 5 попыток; полноэкранный экран блокировки (`FLAG_SECURE`); «Забыли PIN» = выход | DONE | `28709fa` |
| **R2.3 (Аудит 18, 22, 23, 32)** | `pendingCameraUri` в `rememberSaveable`, черновик в `SavedStateHandle`, снят `screenOrientation=portrait`; автоочистка `cacheDir` старше 24 ч; фильтрация `lastKnownLocation` старше 2 мин; очистка APK после обновления | DONE | `926ffec` |
| **R2.4 (Функция 7 + R2.Verify)** | Вход по отпечатку пальца (`BiometricPrompt`, включается в настройках, PIN запасной); модульные тесты Фазы R2, полная проверка и тег `r2-done` | DONE | `1a2fc30` (`r2-done`) |
| **R3.1 (Функция 1)** | Поле `createdAt: Long = 0L` в `CapsuleDto`; на Главной — живой таймер `ДД:ЧЧ:ММ:СС` в `Lifecycle.State.STARTED` и полоса «выдержки» капсулы | DONE | `f2a12cc` |
| **R3.2 (Функция 5)** | Экран «Моя статистика» на Compose Canvas (KPI, серия месяцев, тепловая карта за 6 мес, столбцы по месяцам, кольцо топ-5 тегов, линия будущих открытий, анимация, пустые состояния, TalkBack, `CapsuleStatsCalculator` + тесты) | DONE | `13a79ec` |
| **R3.3 (Аудит 12 клиент)** | UI многоэтапных капсул в `CreateCapsuleScreen` (до 5 этапов, выбор даты/времени и текста каждого этапа, удаление этапов, валидация хронологического порядка) | DONE | `c823d79` |
| **R3.4 (Функция 3, 20-PDF)** | Карточка открытой капсулы (неоновая PNG 1080×1350 без текста письма) и экспорт письма в PDF (`PdfDocument`) через системный Share Sheet и `FileProvider` | DONE | `dda49b1` |
| **R3.5 (Аудит 24, 27, 33 + R3.Verify)** | `contentDescription` для интерактивных иконок и `Canvas`, touch target 48 dp, контраст вторичного текста (`alpha >= 0.78`), переиспользование `Path` (`rewind()`) в `Capsule3DView`; полная проверка и тег `r3-done` | DONE | `610db4c` (`r3-done`) |

---

## 2. Тестирование и размеры артефактов

### Статус автоматических тестов
- **Backend (`./backend/gradlew -p backend test build fatJar --no-daemon`):**
  - Всего тестов: **34** (`ApplicationTest.kt`)
  - Пройдено: **34 / 34 (100%, 0 ошибок)**
  - Покрыты сценарии R1: сохранение открытой гео-капсулы после 61 с (`openedAt != null`), запрет `join` в личную капсулу (`403`), полное удаление строки `Users` и медиа в `DELETE /api/v1/me/data`, бесшовная ротация `CAPSULE_AES_KEY_PREVIOUS` и защита от повреждённого шифротекста, защита от chunked-обхода `Content-Length` (`readNBytes(max + 1)`), батч-загрузка списка капсул за <= 6 SQL-запросов без чтения `encrypted_bytes`, наличие всех 7 индексов БД и `GET /ping`.
- **Android Client (`./android-client/gradlew -p android-client lint testReleaseUnitTest assembleDebug assembleRelease -PapiBaseUrl=https://time-capsule-project-fyyy.onrender.com --no-daemon`):**
  - Всего модульных тестов: **23** (`DtoSerializationTest.kt`: 4 теста; `UnlockAndConfigUnitTest.kt`: 19 тестов)
  - Пройдено: **23 / 23 (100%, 0 ошибок)**
  - Android Lint: **BUILD SUCCESSFUL (0 критических ошибок)**
  - Синхронность DTO (`diff backend/.../ModelsDto.kt android-client/.../ModelsDto.kt`): **0 расхождений (файлы 100% идентичны)**.

### Размеры собранных артефактов
- **Server Fat JAR (`backend/build/libs/timecapsule-server.jar`):** `26 МБ`
- **Android Release APK (`android-client/app/build/outputs/apk/release/app-release.apk`):** `11 МБ` (с R8 minification & resource shrinking)
- **Android Debug APK (`android-client/app/build/outputs/apk/debug/app-debug.apk`):** `66 МБ`

---

## 3. Ключевые архитектурные и инженерные решения (из `DECISIONS.md`)

1. **Проекция метаданных медиа и батч-загрузка (`R1.1`)**: `GET /api/v1/capsules` выбирает из `CapsuleMedia` только метаданные (`id`, `capsuleId`, `kind`, `mime`, `sizeBytes`, `durationMs`) без BLOB `encrypted_bytes` и загружает связанные сущности за 4 батч-запроса `inList` (суммарно 6 SQL-запросов вместе с проверкой сессии).
2. **Навсегда открытые гео-капсулы и `hasUnlockedStage` (`R1.2`)**: При `openedAt != null` у участника гео-условие считается выполненным навсегда (текст и медиа остаются доступными после ухода с локации). Поле `hasUnlockedStage` отвечает за доступность открытых этапов и статус `READY`, а `isFullyUnlocked` — за полное открытие всех этапов.
3. **Бесшовная ротация ключа (`R1.8`)**: Формат шифротекста `[12 байт IV][GCM ciphertext + tag]` сохранён неизменным; при `AEADBadTagException` сервер прозрачно пробует `CAPSULE_AES_KEY_PREVIOUS`. Повреждённая запись логируется без секретов и отдаёт безопасную заглушку, не ломая список.
4. **Экономия compute-часов Neon Free и `/ping` (`R1.9`)**: Пул Hikari настроен на `minimumIdle = 0`, `idleTimeout = 60000`, очистка сессий выполняется раз в 6 часов, а публичный `GET /ping` отвечает `"ok"` без обращения к БД.
5. **Локальные уведомления 2.0 без Firebase (`R2.1`)**: Использование `AlarmManager` (`canScheduleExactAlarms()` с fallback на `setWindow` без запроса спец-разрешения `SCHEDULE_EXACT_ALARM`), `BootCompletedReceiver` (`SharedPreferences` для планов открытия) и `WorkManager` для годовщины через 1 год.
6. **Криптозащита `session_token`, `PBKDF2` и биометрия (`R2.2`, `R2.4`)**: Токен сессии шифруется ключом `AndroidKeyStore` (`AES/GCM/NoPadding`) с прозрачной миграцией; PIN хешируется через `PBKDF2WithHmacSHA256` (60 000 итераций) с миграцией при первом вводе; полноэкранный экран блокировки защищён `FLAG_SECURE` и поддерживает системный `BiometricPrompt`.
7. **Экспорт PNG-карточки и PDF-письма (`R3.4`)**: PNG-карточка 1080×1350 рендерится на `Bitmap + Canvas` строго без секретного текста письма, а PDF-документ генерируется через нативный `android.graphics.pdf.PdfDocument` и передаётся через `FileProvider`.

---

## 4. Что перенесено на будущие итерации (по плану)

Следующие крупные задачи осознанно отложены в соответствии с ночным планом:
1. **Офлайн-кэш Room (`Функция 4`, `Аудит 21`)** — локальная БД Room с таблицами капсул и этапов для полноценного офлайн-чтения (требует отдельной миграции репозитория и схем).
2. **Полная локализация RU / EN / TG (`Улучшение 20 - локализация`, `Аудит 26`)** — вынос всех оставшихся строк в `values-en` / `values-tg` и переключатель языка в настройках.
3. **Виджет рабочего стола Glance (`Функция 6`)** — виджет 4×2 в стиле Tokyo Night с обратным отсчётом до ближайшей капсулы.
4. **QR-коды приглашения (`Улучшение 20 - QR`)** — генерация и сканирование неонового QR-кода для групповых капсул.
5. **Быстрые реакции получателя (`Улучшение 20 - реакции`)** — отправка эмодзи-реакции автору при открытии входящей капсулы.
6. **Соавторство и публичные веб-страницы капсул** — отклонены по решению пользователя.
