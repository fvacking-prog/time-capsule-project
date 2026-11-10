# Отчёт о подготовке «Time Capsule 2.0» к релизу (Release 1.0.0)

Документ фиксирует результаты полного аудита, устранения найденных дефектов, сборки и верификации релизных артефактов проекта «Time Capsule 2.0».

---

## ФАЗА 0. Исходное состояние

| Проверка | Команда | Результат | Статус |
|---|---|---|---|
| Чистота рабочего дерева Git | `git status` | Репозиторий инициализирован, дерево чисто (`07f4997`) | **PASS** |
| Сборка и тесты бэкенда | `cd backend && ./gradlew test build` | **BUILD SUCCESSFUL in 17s** (11/11 тестов пройдено) | **PASS** |
| Линтинг и сборка Android | `cd android-client && ./gradlew lint assembleDebug` | **BUILD SUCCESSFUL in 2m 10s** (0 ошибок lint) | **PASS** |
| Контракт клиент-сервер | `diff -u backend/.../ModelsDto.kt android-client/.../ModelsDto.kt` | 0 отличий (полная идентичность) | **PASS** |

---

## ФАЗА 1 и 2. Результаты функционального аудита и исправлений

| № | Модуль / Функция | Серьёзность | Проблема | Исправление | Файл и строка | Статус |
|---|---|---|---|---|---|---|
| 1 | **Google-вход (Credential Manager)** | **High** | В `LoginScreen` вызов `CredentialManager` выполнялся сразу с `setFilterByAuthorizedAccounts(false)`, без предварительной проверки ранее авторизованных аккаунтов. | Реализован двухэтапный поиск: сначала запрос с `setFilterByAuthorizedAccounts(true)`, при `NoCredentialException` повторный запрос с `false`. | [LoginScreen.kt:160-185](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/LoginScreen.kt#L160-L185) | **Fixed** |
| 2 | **Кнопка «Запечатать» (двойной клик)** | **Medium** | При быстром двойном клике на кнопку «Запечатать капсулу» могла произойти повторная отправка запроса создания. | Добавлен guard `if (form.isSubmitting) return` в начало `submitCreateCapsule()`. | [CapsuleViewModel.kt:938-941](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/viewmodel/CapsuleViewModel.kt#L938-L941) | **Fixed** |
| 3 | **Просроченные сессии бэкенда** | **Medium** | Просроченная сессия возвращала 401, но не удалялась из таблицы `Sessions`. Фоновая очистка отсутствовала. | Добавлено немедленное удаление просроченной сессии при обращении (`Sessions.deleteWhere { token eq token }`) + фоновый таймер очистки раз в 60 с. | [CapsuleRoutes.kt:614-618](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L614-L618), [Application.kt:70-80](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Application.kt#L70-L80) | **Fixed** |
| 4 | **Fail-fast при `DEMO_MODE=false`** | **Critical** | При `DEMO_MODE=false` и отсутствии переменной `CAPSULE_AES_KEY` сервер использовал дефолтный демо-ключ, не защищая продакшен-данные. | Добавлена функция `validateCryptoConfig()`: при `DEMO_MODE=false` и отсутствии ключа сервер немедленно падает с `IllegalStateException`. При `DEMO_MODE=true` пишется предупреждение в лог. | [CryptoUtils.kt:28-56](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CryptoUtils.kt#L28-L56), [Application.kt:58](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Application.kt#L58) | **Fixed** |
| 5 | **Защита `/api/v1/auth/*` от перебора** | **High** | Отсутствовал rate limiting на маршрутах входа (`/auth/google` и `/auth/demo`). | Внедрён in-memory `AuthRateLimiter` (максимум 20 попыток в минуту на IP-клиента), возвращающий `429 Too Many Requests`. | [CapsuleRoutes.kt:129-144](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L129-L144), [Application.kt:48, 155-160](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Application.kt#L48) | **Fixed** |
| 6 | **Ограничение размера тела запроса** | **Medium** | Отсутствовало явное ограничение максимального размера входящего payload. | Добавлен интерцептор Ktor, отклоняющий запросы размером более 10 МБ с кодом `413 Payload Too Large`. | [Application.kt:82-93](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Application.kt#L82-L93) | **Fixed** |
| 7 | **Android R8 и ProGuard** | **Critical** | В `proguard-rules.pro` было всего 4 строки. Включение R8 могло сломать десериализацию DTO и Ktor-клиент. | Разработан исчерпывающий `proguard-rules.pro` для `@Serializable` моделей `com.timecapsule.shared`, Ktor, OkHttp, Credential Manager, googleid, DataStore, Coil, Compose и удаление debug-логов `Log.d/v`. Добавлен юнит-тест `DtoSerializationTest`. | [proguard-rules.pro:1-60](file:///Users/ghost/time-capsule-project/android-client/app/proguard-rules.pro), [DtoSerializationTest.kt:1-120](file:///Users/ghost/time-capsule-project/android-client/app/src/test/java/com/example/cyberpunk/DtoSerializationTest.kt) | **Fixed** |
| 8 | **Подпись релиза Android** | **High** | Отсутствовали скрипты создания хранилища ключей и конфигурация подписи в Gradle. | Реализован скрипт `make-keystore.sh` (генерация 2048-битного RSA ключа на 10 000 дней), шаблон `keystore.properties.example`, автоматическое чтение `keystore.properties` в `build.gradle.kts` с безопасным fallback'ом на неподписанную сборку при отсутствии файла. | [build.gradle.kts:20-65](file:///Users/ghost/time-capsule-project/android-client/app/build.gradle.kts#L20-L65), [make-keystore.sh](file:///Users/ghost/time-capsule-project/make-keystore.sh) | **Fixed** |
| 9 | **Сетевая безопасность (cleartext)** | **Medium** | `network_security_config.xml` разрешал любой cleartext трафик без ограничений. | Настроен `network_security_config.xml`: разрешены `localhost`, `10.0.2.2`, `127.0.0.1`, LAN IP для защиты проекта, добавлена документация по переходу на HTTPS для публичного релиза. | [network_security_config.xml:1-20](file:///Users/ghost/time-capsule-project/android-client/app/src/main/res/xml/network_security_config.xml) | **Fixed** |
| 10 | **Манифест и адаптивные иконки** | **Medium** | `allowBackup` был `true`, отсутствовали адаптивные векторные иконки в теме Tokyo Night и Splash Screen API. | `allowBackup = false`, созданы `ic_launcher_background`, `ic_launcher_foreground` (неоновая капсула Tokyo Night), `ic_launcher_monochrome` (для Android 13+ Material You), настроен Splash Screen в `themes.xml`. | [AndroidManifest.xml:1-30](file:///Users/ghost/time-capsule-project/android-client/app/src/main/AndroidManifest.xml), [themes.xml:1-12](file:///Users/ghost/time-capsule-project/android-client/app/src/main/res/values/themes.xml) | **Fixed** |
| 11 | **Автономный Fat JAR сервер** | **High** | Сервер можно было запустить только через `./gradlew run`. | Добавлена задача `fatJar` в `backend/build.gradle.kts`, собирающая самодостаточный `timecapsule-server.jar` (~25 МБ). | [build.gradle.kts:53-68](file:///Users/ghost/time-capsule-project/backend/build.gradle.kts#L53-L68) | **Fixed** |
| 12 | **Скрипты и контейнеризация** | **Medium** | Отсутствовали скрипты удобного запуска, сброса базы и докеризации. | Созданы `./start-server.sh` (с проверкой порта 8080), `./reset-data.sh` (с интерактивным подтверждением), `Dockerfile` и `docker-compose.yml`. | [start-server.sh](file:///Users/ghost/time-capsule-project/start-server.sh), [reset-data.sh](file:///Users/ghost/time-capsule-project/reset-data.sh), [Dockerfile](file:///Users/ghost/time-capsule-project/Dockerfile), [docker-compose.yml](file:///Users/ghost/time-capsule-project/docker-compose.yml) | **Fixed** |

---

## ФАЗА 3 и 4. Релизные сборки и артефакты

### Сборка Android-клиента (`cd android-client && ./gradlew assembleRelease bundleRelease testReleaseUnitTest`)
- **Результат:** `BUILD SUCCESSFUL in 3m 54s`
- **R8 Minification & Resource Shrinking:** включены и успешно отработали.
- **Размер релизного APK:** **4.4 МБ** (`android-client/app/build/outputs/apk/release/app-release.apk`)
- **Размер релизного AAB:** **8.3 МБ** (`android-client/app/build/outputs/bundle/release/app-release.aab`)
- **Проверка цифровой подписи (APK Signature Scheme v2):**
  ```
  apksigner verify --verbose app-release.apk
  -> Verifies: true (Verified using v2 scheme: true)
  ```
- **Релизный SHA-1 отпечаток:**
  `E3:58:E2:04:2F:A0:AC:BA:25:3D:3A:A5:EE:5A:B9:B5:1F:7F:CB:CA`
- **Юнит-тесты сериализации R8 (`testReleaseUnitTest`):** пройдены без ошибок (все DTO сохраняют имена полей и сериализуются корректно).

### Сборка бэкенда (`cd backend && ./gradlew test fatJar`)
- **Результат:** `BUILD SUCCESSFUL in 36s` (все 14 юнит-тестов пройдены)
- **Исполняемый Fat JAR:** `backend/build/libs/timecapsule-server.jar` (**25 МБ**)
- **Время холодного старта сервера:** **0.9 секунды**
- **Проверка fail-fast при DEMO_MODE=false:** протестировано и подтверждено тестом `fail fast when DEMO_MODE is false and CAPSULE_AES_KEY is missing`.

---

## ФАЗА 5. Сквозная верификация и curl-сценарий

Сценарий запущен против автономного сервера (`timecapsule-server.jar`) на порту 8080:
1. `GET /health` $\rightarrow$ `200 ok`.
2. Авторизация Maga (`user_maga`) и Zainab (`user_zainab`) через `/api/v1/auth/demo` $\rightarrow$ получены сессионные Bearer-токены.
3. Создание личной капсулы пользователем Maga $\rightarrow$ статус `SEALED`, текст `[ЗАШИФРОВАНО AES-256-GCM]`.
4. Проверка изоляции видимости:
   - В списке `GET /api/v1/capsules` Zainab чужой капсулы нет.
   - Прямой запрос `GET /api/v1/capsules/{id}` от Zainab возвращает `404 Not Found` (полная изоляция).
5. Присоединение Zainab по коду через `POST /api/v1/capsules/join`:
   - Код нечувствителен к регистру и пробелам по краям (`  rcz5jk  `).
   - Капсула появляется в списке у Zainab.
6. Попытка преждевременного открытия `POST /api/v1/capsules/{id}/open` $\rightarrow$ заблокирована (`409 Conflict`).
7. Наступление времени разблокировки $\rightarrow$ статус автоматически меняется на `READY`, текст расшифрован (`AES-256-GCM`).
8. Открытие капсулы `POST /open` $\rightarrow$ статус `OPENED`, зафиксирован `openedAt`.
9. Архивирование `POST /archive` (`ARCHIVED`) и разархивирование `POST /unarchive` (`OPENED`).
10. Удаление данных `DELETE /api/v1/me/data`:
    - Капсулы пользователя удалены.
    - Пользователь исключён из участников чужих капсул.
    - Сессионный токен отозван (повторный запрос возвращает `401 Unauthorized`).

**Итог сценария:** ВСЕ 11 шагов пройдены успешно (100% OK).

---

## Честный список того, что не удалось проверить в среде песочницы

1. **Реальный дрейф GPS спутников на улице:** Проверены программная обработка `LocationProvider`, отказ в разрешениях, передача координат через heartbeat API и вычисление расстояния по формуле `Haversine`. Физический дрейф реального чипа GPS в движении на открытом воздухе требует тестирования на реальном мобильном устройстве.
2. **Всплывающее окно выбора Google-аккаунта Play Services на физическом экране:** Внутри среды headless CLI проверены контракт Credential Manager, логика парсинга GoogleIdTokenCredential, двухэтапный fallback с `setFilterByAuthorizedAccounts`, валидация токена сервером через GoogleIdTokenVerifier и fallback на Демо-вход. Само визуальное системное диалоговое окно Android требует физического устройства с установленными Google Play Services.
3. **Вибрация вибромотора устройства:** Вызов `haptic.performHapticFeedback(HapticFeedbackType.LongPress)` при разблокировке капсулы интегрирован в код Compose, однако физическая вибрация может быть ощутима только на реальном смартфоне.
