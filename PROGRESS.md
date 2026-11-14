# PROGRESS.md

Отслеживание прогресса автономной ночной работы (ветка `overnight`).

| Пункт | Описание | Статус | Коммит |
|---|---|---|---|
| **R1.1 (Аудит 1, 8)** | Список капсул не читает `encrypted_bytes` (проекция метаданных); батч-загрузка через `inList` (не более 6 SQL-запросов) | DONE | `8ad0c54` |
| **R1.2 (Аудит 2, 12)** | Если `openedAt != null`, гео-условие выполнено навсегда (текст и медиа); `hasUnlockedStage` в `CapsuleDto` и клиенте; `isFullyUnlocked` = все этапы открыты | DONE | `c4f2249` |
| **R1.3 (Аудит 5)** | `DELETE /api/v1/me/data` удаляет строку `Users` и медиа | DONE | `6a6cc7a` |
| **R1.4 (Аудит 6)** | `inviteCode` только для групповых капсул; `join` только в групповые (`403` для личных); поиск по `trim().uppercase()` без `selectAll`; двусторонняя проверка блокировок | DONE | `6cbe1ff` |
| **R1.5 (Аудит 7)** | `AuthRateLimiter`: ключ = последнее значение `X-Forwarded-For` (или `remoteHost`), очистка > 5000 ключей, лимит на `/capsules/join`, `/me/handle` и загрузку медиа | DONE | `a2e1195` |
| **R1.6 (Аудит 11)** | Чтение загрузок ограничено по байтам (`readNBytes(max + 1)`), защита от chunked-обхода `Content-Length` | DONE | `7d4d539` |
| **R1.7 (Аудит 9, 10)** | Индексы БД (7 индексов + проверка/создание в PostgreSQL); поиск `@handle` через SQL `like` + `limit(30)` и исключение заблокированных | DONE | `6f0e409` |
| **R1.8 (Аудит 15)** | Ротация ключа без смены формата шифротекста (`CAPSULE_AES_KEY_PREVIOUS` при `AEADBadTagException`), защита от падения списка на повреждённых данных | DONE | `pending` |
| **R1.9 (Аудит 16, 20, 28, 31, 34, 35 + /ping)** | Кешируемый `GoogleIdTokenVerifier`; Hikari `minimumIdle=0`, `idleTimeout=60000`, очистка просроченных сессий (при старте и раз в 6 ч); `409` при дубле `@handle`; стектрейсы в `StatusPages` и `CallLogging`; обновление `google-api-client` и `postgresql`; публичный `GET /ping`; унификация корневого `Dockerfile` | TODO | — |
| **R1.10 (Аудит 4, 14, 17, 19, 29)** | Клиент: убрать двойную навигацию с полки; `retryTrigger` в `PhotoMediaCard`/`VoiceMediaCard` и перемотка `Slider`; убрать повторный `authGoogle` в `catch`; бейдж кода только у групповых; разблокировать шаблоны в `IdeasScreen`, удалить `ROUTE_MY_CAPSULES` и мёртвые методы | TODO | — |
| **R1.Verify** | Обязательные тесты Фазы R1, сборка backend/android, проверка `diff ModelsDto.kt`, curl-сценарий на `fatJar` (порт 18080), тег `r1-done` | TODO | — |
| **R2.1 (Аудит 3, Функция 2)** | `POST_NOTIFICATIONS` (API 33+), 3 канала уведомлений, `AlarmManager` (`canScheduleExactAlarms()` + fallback `setWindow`), `BootCompletedReceiver`, годовщина через `WorkManager`, тихие часы 23:00-08:00, статус разрешения в настройках, обработка `EXTRA_CAPSULE_ID` в `MainActivity` | TODO | — |
| **R2.2 (Аудит 13)** | Шифрование `session_token` через Android Keystore (AES/GCM) с миграцией; PIN `PBKDF2WithHmacSHA256` (60000 итераций) с миграцией; кулдаун 30 с после 5 попыток; полноэкранный экран блокировки (`FLAG_SECURE`); «Забыли PIN» = выход | TODO | — |
| **R2.3 (Аудит 18, 22, 23, 32)** | `pendingCameraUri` в `rememberSaveable`, черновик в `SavedStateHandle`, убрать `screenOrientation=portrait`; очистка `cacheDir` старше 24 ч при старте; отбрасывать `lastKnownLocation` старше 2 мин; очистка APK после обновления | TODO | — |
| **R2.4 (Функция 7 + R2.Verify)** | Вход по отпечатку (`androidx.biometric`, включается в настройках, PIN запасной); тесты Фазы R2, полная проверка фазы и тег `r2-done` | TODO | — |
| **R3.1 (Функция 1)** | `createdAt` в `CapsuleDto` (`0L` по умолчанию); на Главной живой таймер (`ДД:ЧЧ:ММ:СС` в `STARTED`) и полоса «выдержки» | TODO | — |
| **R3.2 (Функция 5)** | Экран «Моя статистика» на Compose Canvas (KPI, тепловая карта за 6 мес, столбцы по месяцам, кольцо по тегам, линия будущих открытий, анимация, пустые состояния, TalkBack, `CapsuleStatsCalculator` + тесты) | TODO | — |
| **R3.3 (Аудит 12 клиент)** | UI многоэтапных капсул в `CreateCapsuleScreen` (до 5 этапов, дата и текст, валидация дат по порядку) | TODO | — |
| **R3.4 (Функция 3, 20-PDF)** | Карточка открытой капсулы (неоновая PNG-картинка без текста письма) и PDF-письмо (`PdfDocument`) + системный Share Sheet через `FileProvider` | TODO | — |
| **R3.5 (Аудит 24, 27, 33 + R3.Verify)** | `contentDescription` для интерактивных иконок и Canvas, touch target 48 dp, `onSurfaceVariant` и контраст (alpha >= 0.78), переиспользование `Path` в `Capsule3DView`; полная проверка фазы и тег `r3-done` | TODO | — |
| **FINAL** | Обновление `CHANGELOG.md`, `README.md`, `docs/privacy.html`; создание `OVERNIGHT_REPORT.md` и `MORNING_CHECKLIST_2.md` | TODO | — |
