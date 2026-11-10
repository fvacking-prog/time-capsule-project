# Аудит проекта «Time Capsule 2.0» (Backend + Android Client)

## Сводка проверок Фазы 1

1. **Сборка:**
   - `backend`: `./gradlew build` — проходит успешно (`BUILD SUCCESSFUL`), 0 ошибок, 0 предупреждений компилятора.
   - `android-client`: `./gradlew assembleDebug` — проходит успешно (`BUILD SUCCESSFUL`), 0 ошибок, 0 предупреждений компилятора.
2. **Контракт клиент/сервер (`ModelsDto.kt`):**
   - `diff -u backend/.../ModelsDto.kt android-client/.../ModelsDto.kt` — файлы **100% идентичны** (пакет `com.timecapsule.shared`).
   - Настройки `Json { ignoreUnknownKeys = true; encodeDefaults = true }` идентичны на сервере (`Application.kt:66-69`) и клиенте (`CapsuleRepository.kt:51-55`).
   - Все эндпоинты (`/health`, `/api/v1/capsules`, `/api/v1/capsules/{id}`, `/api/v1/capsules/{id}/check-in`, `/api/v1/capsules/{id}/demo-force-checkin`) и DTO совпадают.

---

## Таблица найденных замечаний (Фаза 1) и статус исправлений (Фаза 2)

| № | файл:строка | Серьёзность | Проблема | Как исправить | Статус |
|---|---|---|---|---|---|
| 1 | `backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt:128` | **Critical** | Эндпоинт `POST /api/v1/capsules/{id}/demo-force-checkin` был доступен безусловно. Любой клиент мог принудительно разблокировать гео-условие любой капсулы в продакшене. | Разрешать вызов `/demo-force-checkin` только при включённом флаге окружения `DEMO_MODE=true` (по умолчанию выключено — возвращать `403 Forbidden` с текстовым телом). | **Fixed** |
| 2 | `backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt:48` | **High** | В `POST /api/v1/capsules` и `POST /api/v1/capsules/{id}/check-in` отсутствовала валидация входных данных: не проверялись длина `title` (`<= 255`), непустота и размер `stages`, длина `secretText`, диапазон координат `lat in -90.0..90.0`, `lon in -180.0..180.0` (включая `NaN`/`Infinity`), `radiusMeters > 0`, корректность `unlockDate > 0`. | Добавлена строгая валидация `validateCreateCapsuleRequest` и `validateCheckInRequest` с выбросом `IllegalArgumentException` (`400 Bad Request`). | **Fixed** |
| 3 | `backend/src/main/kotlin/com/timecapsule/server/GeoUtils.kt:38` | **High** | В `GeoUtils.isNearbyAndRecent` условие `(now - lastSeenAt) <= RECENT_THRESHOLD_MS` не проверяло `now >= lastSeenAt` (при `lastSeenAt > now` разность отрицательна и проходила проверку `<= 60_000`), а также не отсекало `radiusMeters <= 0.0` и невалидные координаты (`NaN`, выход за `[-90..90]`/`[-180..180]`). | Добавлены `GeoUtils.isValidCoordinate`, проверка `radiusMeters > 0.0` и проверка `(now - lastSeenAt) in 0L..RECENT_THRESHOLD_MS`. | **Fixed** |
| 4 | `android-client/app/src/main/java/com/example/cyberpunk/ui/screens/RadarScreen.kt:138` | **High** | Циклы heartbeat в `LaunchedEffect` отменялись при уходе с экрана в `NavHost`, но продолжали выполняться в фоне при сворачивании приложения (`ON_STOP`), расходуя батарею и вызывая GPS/сеть в фоне. | Оба цикла heartbeat обёрнуты в `lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED)`. | **Fixed** |
| 5 | `backend/src/main/kotlin/com/timecapsule/server/Application.kt:74` | **Medium** | В `StatusPages` при ошибках парсинга JSON (`BadRequestException`, `ContentConvertException`, `SerializationException`) наружу отдавался `cause.message`, который мог содержать внутренние имена классов/полей; отсутствовал глобальный `exception<Throwable>`. | Для ошибок парсинга JSON возвращается фиксированный текст `"Invalid JSON payload"`, добавлены `ForbiddenException` (`403`) и fallback `exception<Throwable>` (`500 Internal server error` без стектрейса). | **Fixed** |
| 6 | `android-client/app/src/main/java/com/example/cyberpunk/data/CapsuleRepository.kt:46` | **Medium** | Таймауты Ktor Client в `RemoteCapsuleRepository` были выставлены в `5000`/`8000` мс вместо требуемых по спецификации `10_000` мс (10 секунд). | Установлены `requestTimeoutMillis = 10_000L`, `connectTimeoutMillis = 10_000L`, `socketTimeoutMillis = 10_000L` и таймауты `OkHttp` engine в 10 секунд. | **Fixed** |
| 7 | `android-client/app/src/main/java/com/example/cyberpunk/ui/components/Capsule3DView.kt:680` и `RadarScreen.kt:412` | **Medium** | Внутри `Canvas` draw-фазы на каждом кадре создавались объекты `Brush.radialGradient(colors = listOf(...))` и `Stroke(...)`, что создавало лишнюю нагрузку на GC при 60 FPS. | Удалены покадровые аллокации `Brush.radialGradient` и `listOf(...)` в `drawCyberpunkBackdrop` и `RadarScreen`; объекты `Stroke` вынесены в константы уровня файла. | **Fixed** |
| 8 | `android-client/app/src/main/java/com/example/cyberpunk/data/CapsuleRepository.kt:269` | **Medium** | В `MockCapsuleRepository.toDto` условие `(now - p.lastSeenAt!!) <= 60_000L` не проверяло `now >= lastSeenAt` и конечность координат. | Исправлено на `(now - seenAt) in 0L..60_000L` с проверкой `lat.isFinite() && lon.isFinite()` и `radiusMeters > 0.0`. | **Fixed** |
| 9 | `android-client/app/src/main/java/com/example/cyberpunk/ui/viewmodel/CapsuleViewModel.kt:50` | **Low** | Поле `errorMessage: String? = null` в `CapsuleUiState` никогда не читалось в UI (все ошибки отображаются через `snackbarEvents`). | Мёртвое поле `errorMessage` удалено из `CapsuleUiState`. | **Fixed** |
| 10 | `backend/src/main/kotlin/com/timecapsule/server/ModelsDto.kt:2` | **Low** | Файл `ModelsDto.kt` имеет `package com.timecapsule.shared`, но расположен в каталоге `com/timecapsule/server`. | Оставлено в исходном расположении согласно требованию п.1 ТЗ («Скопируй без изменений ModelsDto.kt»). | **Won't Fix (By Design)** |

---

## Аудит Фазы 4: Вход через Google-аккаунт, Bearer-сессии и защита персональных данных

| Аспект проверки | Описание и соответствие требованиям | Статус |
|---|---|---|
| **Устранение хардкода Client ID** | Web Client ID считывается из `local.properties` (`GOOGLE_WEB_CLIENT_ID`) на Android и из переменной окружения `GOOGLE_WEB_CLIENT_ID` на сервере. Конфиденциальные ключи не хранятся в репозитории (`.gitignore`). | **Pass** |
| **Верификация Google ID Token** | Использован `GoogleIdTokenVerifier` из `com.google.api-client`. Проверяются криптографическая подпись, срок действия, `audience == GOOGLE_WEB_CLIENT_ID`, издатели `accounts.google.com` и `https://accounts.google.com`, а также флаг `email_verified == true`. | **Pass** |
| **Защита API и Bearer-сессии** | Все маршруты `/api/v1/capsules/*` защищены `call.requireAuthenticatedUserId()`. Запросы без заголовка `Authorization: Bearer <token>` или с невалидным/просроченным токеном немедленно отклоняются со статусом `401 Unauthorized`. | **Pass** |
| **Предотвращение подделки личности (Spoofing)** | Создатель капсулы и автор чекина берутся исключительно из проверенного Bearer-токена. Попытка чекина за другого участника блокируется со статусом `403 Forbidden`. | **Pass** |
| **Защита персональных данных (Privacy)** | Эндпоинт `GET /api/v1/users?query=` маскирует адреса электронной почты других пользователей (`m***a@gmail.com`). Пользователь видит свой полный email только в `/api/v1/me`. | **Pass** |
| **Современный Android Credential Manager** | Устаревший `GoogleSignInClient` не используется. Авторизация реализована через `CredentialManager`, `GetGoogleIdOption` и `GoogleIdTokenCredential` с корректной обработкой отмены пользователем и отсутствия аккаунта. | **Pass** |
| **Надёжность и отказоустойчивость** | Сохранён демо-вход (`POST /api/v1/auth/demo`), позволяющий успешно продемонстрировать работу проекта на защите даже в оффлайн-режиме без интернета или Google-аккаунта на эмуляторе. | **Pass** |
