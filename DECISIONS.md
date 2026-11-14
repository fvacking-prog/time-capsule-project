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

## Доработка: Google Sign-In, улучшенная геолокация и радиус 50–500 м
1. **Google Sign-In через GetSignInWithGoogleOption**:
   - Переход на единый `GetCredentialRequest` с `GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()`.
   - Удален двухшаговый fallback через `GetGoogleIdOption`.
   - Сохранены понятные сообщения об ошибках (отмена, отсутствие Google-аккаунта, проблемы с сетью, DEVELOPER_ERROR). При выходе выполняется `clearCredentialState(ClearCredentialStateRequest())` и очистка локальной сессии.
2. **Определение геолокации с fallback на LocationManager**:
   - Проверка системного статуса геолокации (`LocationManagerCompat.isLocationEnabled`). При выключенной геолокации показывается диалог с переходом в настройки геолокации (`Settings.ACTION_LOCATION_SOURCE_SETTINGS`).
   - Первичный запрос через `FusedLocationProviderClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY)` с таймаутом 5 секунд (`withTimeoutOrNull(5_000L)`).
   - При сбое Play Services или возврате `null` — fallback на `LocationManager` (`GPS_PROVIDER` и `NETWORK_PROVIDER`, API 30+ `getCurrentLocation`, иначе `requestSingleUpdate` с таймаутом).
   - При предоставлении только Coarse-разрешения выводится информационный Snackbar с предупреждением о сниженной точности.
   - При отказе в разрешениях выводится Snackbar с кнопкой перехода в настройки приложения (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
   - При уходе с экрана корутина запроса локации немедленно отменяется (`DisposableEffect` -> `cancelLocationFetch()`).
3. **Радиус гео-капсулы (сервер: 20..500 м, клиент: 50..500 м)**:
   - Сервер строго валидирует радиус в диапазоне `20.0..500.0` метров для гео-капсул (покрыто юнит-тестами).
   - Клиент использует `Slider` в диапазоне `50..500` м (значение по умолчанию: 50 м) с горизонтальным отступом контейнера `Modifier.padding(horizontal = 8.dp)`, исключающим обрезку ползунка слева.
   - Добавлены быстрые чипы выбора `50 / 100 / 200 / 500 м` и текст-подсказка: «В помещении GPS неточен: берите больше (100–200 м)». Отображение радиуса оформлено в виде: «Радиус: 50 м».

## 2026-11-12: Медиавложения (Фото и Голос) с шифрованием на сервере
1. **Архитектура шифрования и хранения медиафайлов**:
   - Новая таблица `CapsuleMedia` хранит зашифрованные бинарные данные (`encryptedBytes`) с использованием AES-256-GCM.
   - Для каждого медиафайла генерируется криптографически стойкий случайный 12-байтный вектор инициализации (IV), конкатенируемый с шифротекстом: `[12 байт IV][GCM ciphertext + 16 байт auth tag]`.
   - В текстах приложения и политике конфиденциальности строго используется формулировка «шифрование на сервере» без упоминания «сквозного шифрования».
2. **Лимиты и валидация на сервере**:
   - Максимум 1 фото (до 800 КБ) и 1 голосовое сообщение (до 1 МБ) на капсулу.
   - Максимальный размер тела создания капсулы ограничен 2.5 МБ.
   - Белый список MIME-типов: `image/jpeg` и `audio/mp4`.
   - Проверка сигнатур файлов: JPEG (`FF D8 FF`), MP4 (`ftyp` со смещения 4).
   - Лимит создания вложений: максимум 20 медиавложений в сутки на пользователя.
3. **Безопасная выдача медиа**:
   - Эндпоинт `GET /api/v1/capsules/{id}/media/{mediaId}` расшифровывает и отдаёт байты только при выполнении условий открытия капсулы (наступила дата открытия, выполнены условия присутствия для гео-капсул) и только авторизованным участникам капсулы.
   - Заголовок `Cache-Control: no-store` предотвращает кэширование незашифрованных данных промежуточными прокси-серверами.
4. **Клиентская реализация (Android)**:
   - Фото: системный селектор `PickVisualMedia` и камера через `TakePicture` с `FileProvider` (`cacheDir/capsule-media/`). Разрешение `android.permission.CAMERA` не требуется и не объявляется в манифесте.
   - Автоматическое масштабирование фото до 1280 px по длинной стороне и сжатие в JPEG (качество 75) с сохранением ориентации через `ExifInterface`.
   - Голос: `MediaRecorder` (MPEG_4 / AAC, битрейт 64 kbps, 44.1 кГц) с запросом `RECORD_AUDIO`, таймером до 60 секунд, автоостановкой, предпросмотром, воспроизведением (`MediaPlayer`) и удалением.
   - Воспроизведение в открытой капсуле: встроенный аудиоплеер с индикацией прогресса и полноэкранный просмотр фото.
   - Очистка кэша медиафайлов: при выходе из аккаунта (`logout`) временная папка `cacheDir/capsule-media/` полностью очищается.
5. **R8 и ProGuard**:
   - Правило `-keep class com.timecapsule.shared.** { *; }` сохраняет структуру DTO при минификации release-сборок.

## 2026-11-12: Улучшение визуального стиля Tokyo Night
1. **Палитра Tokyo Night и градиенты**:
   - Строго зафиксированы цвета: `#0F111A` (TokyoDeep), `#16161E` (TokyoNight), `#1A1B26` (TokyoBackground), `#24283B` (TokyoSurface), `#7AA2F7` (TokyoPrimary), `#BB9AF7` (TokyoSecondary), `#7DCFFF` (TokyoAccent), `#9ECE6A` (TokyoSuccess), `#F7768E` (TokyoError).
   - Внедрены градиенты `TokyoBorderGradient` и `TokyoPrimaryGradient` (`#7AA2F7` → `#BB9AF7`).
2. **Карточки активных секций**:
   - Ключевые активные карточки (шаги создания капсулы, вход в профиль, выбор никнейма, медиа-секция, открываемые капсулы) получили тонкую неоновую рамку 1.dp с градиентом `#7AA2F7` → `#BB9AF7` и аккуратные внутренние отступы (18.dp).
3. **Сегментированный неоновый переключатель типа капсулы**:
   - Компонент `CapsuleTypeSegmentedSwitch` заменил стандартные чипы выбора («Сообщение», «Фото», «Голос»).
   - Анимированное переключение через `animateColorAsState` для фона, цвета текста/иконки и неоновой рамки сегмента.
   - Оптимизирован для маленьких экранов и увеличенного шрифта (`weight(1f)`, `maxLines = 1`, `TextOverflow.Ellipsis`).
4. **Кнопка «Запечатать капсулу»**:
   - Градиентный фон `#7AA2F7` → `#BB9AF7` с контрастным текстом и иконкой замка (`#0F111A`, контраст > 8:1).
   - Индикация загрузки (`CircularProgressIndicator` в тон контенту кнопки) при отправке формы.
   - Сохранена логика вызова подтверждения запечатывания.

## 2026-11-14: Ночная доработка (Фазы R1–R3)
1. **R1.1 (Аудит 1 и 8): Проекция метаданных медиа без `encrypted_bytes` и батч-загрузка списка капсул**:
   - В `buildCapsuleDtosBatch` и `buildCapsuleDto` выборка из `CapsuleMedia` выполняется строго по колонкам `id`, `capsuleId`, `kind`, `mime`, `sizeBytes`, `durationMs` без чтения BLOB-поля `encrypted_bytes` (чтение и расшифровка BLOB оставлены только в `GET /api/v1/capsules/{id}/media/{mediaId}`).
   - `getVisibleCapsuleIds` переведён на одиночный SQL-запрос с подзапросами `inSubQuery`/`notInSubQuery`, а `buildCapsuleDtosBatch` загружает капсулы (с `LEFT JOIN Users`), участников, этапы и метаданные медиа за 4 батч-запроса `inList`. Суммарно `GET /api/v1/capsules` выполняет 5 SQL-запросов в транзакции (6 включая проверку сессии), независимо от числа капсул.
2. **R1.2 (Аудит 2 и 12): Постоянная разблокировка открытой гео-капсулы (`openedAt != null`) и `hasUnlockedStage` vs `isFullyUnlocked`**:
   - Если у участника `openedAt != null`, гео-условие считается выполненным навсегда (`geoSatisfied = !isGeoLocked || myOpenedAt != null || allParticipantsNearbyAndRecent`) как при сборке `CapsuleDto` (расшифровка текста этапов), так и при выдаче медиа (`GET /api/v1/capsules/{id}/media/{mediaId}`).
   - В `CapsuleDto` обоих модулей аддитивно добавлено поле `val hasUnlockedStage: Boolean = false`. Поле `hasUnlockedStage` истинно, когда открыт хотя бы один этап, а `isFullyUnlocked` — когда открыты все этапы (`stages.all { it.isUnlocked }`).
   - Статус `READY`, проверка `POST /api/v1/capsules/{id}/open`, доступ к медиа и клиентские проверки открытия (Радар, Полка капсул, Таймлайн, Главная, `CapsuleViewModel`) переведены на `hasUnlockedStage`.
3. **R1.3 (Аудит 5): Полное удаление записи `Users` и медиа при `DELETE /api/v1/me/data`**:
   - В транзакции `DELETE /api/v1/me/data` после каскадного удаления медиа (`CapsuleMedia`), этапов (`CapsuleStages`), участий (`CapsuleParticipants`), капсул (`Capsules`), блокировок (`Blocks`) и сессий (`Sessions`) добавлено удаление самой строки пользователя `Users.deleteWhere { id eq authenticatedUserId }`.
4. **R1.4 (Аудит 6): Коды приглашения только для групповых капсул, запрет `join` в личные (`403`) и двусторонние блокировки**:
   - Генерация `inviteCode` при создании капсулы и при миграции `migrateDatabase()` выполняется только для групповых капсул (`isGroupCapsule == true`), для личных капсул `inviteCode = null`.
   - В `joinCapsuleByInviteCode` поиск выполняется строго по `Capsules.inviteCode eq rawInviteCode.trim().uppercase()` без полного сканирования `selectAll()`. При попытке присоединиться к личной капсуле (`!isGroupCapsule`) выбрасывается `ForbiddenException` (`403 Forbidden`).
   - Проверка блокировок (`isEitherUserBlocked`) переведена в двусторонний режим (блокировка в любую сторону запрещает `join`, доступ к капсуле, видимость в списке и отправку новых капсул).
5. **R1.5 (Аудит 7): Усиление `AuthRateLimiter` (последний IP в `X-Forwarded-For`, очистка > 5000 ключей, защита чувствительных маршрутов)**:
   - В `AuthRateLimiter.extractClientIp` берётся последнее непустое значение заголовка `X-Forwarded-For` (добавляемое доверенным реверс-прокси Render), что исключает обход лимита подделкой начала заголовка клиентом; при отсутствии заголовка используется `remoteHost`.
   - При превышении 5000 ключей в карте `attempts` автоматически запускается `evictStaleEntries(now)` для удаления устаревших записей старше 60 с.
   - Лимитер подключён к `/api/v1/auth/google`, `/api/v1/capsules/join`, `PUT /api/v1/users/me/handle` и загрузке медиа в `POST /api/v1/capsules`.
6. **R1.6 (Аудит 11): Защита от chunked-обхода `Content-Length` через `readNBytes(max + 1)`**:
   - Чтение JSON-тел запросов переведено на `receiveJsonWithLimit` и `readBytesWithLimit(stream, maxBytes)`, использующие `InputStream.readNBytes(maxBytes + 1)`.
   - При превышении лимита (2.5 МБ для `POST /api/v1/capsules`, 64 КБ для остальных JSON-запросов) чтение немедленно прерывается и возвращается `413 Payload Too Large` даже при отсутствии заголовка `Content-Length` (`Transfer-Encoding: chunked`).
7. **R1.7 (Аудит 9 и 10): 7 индексов БД и SQL-поиск пользователей по `@handle` (`like` + `limit(30)` + исключение блокировок)**:
   - В `Tables.kt` объявлены индексы на `Capsules.creatorId`, `CapsuleStages.capsuleId`, `CapsuleParticipants.userId`, `CapsuleMedia.capsuleId`, `Sessions.userId`, `Sessions.expiresAt`, `Blocks.blockedUserId`. В `migrateDatabase()` при старте дополнительно выполняются явные `CREATE INDEX IF NOT EXISTS ...` для гарантированного создания в PostgreSQL и H2.
   - `searchUsersByQuery` переписан без выгрузки всей таблицы `Users` в память: фильтрация выполняется на стороне СУБД через `lowerCase() like ...`, `notInSubQuery` (исключение заблокированных в обе стороны), `orderBy(Users.handle)` и `.limit(30)`.
8. **R1.8 (Аудит 15): Бесшовная ротация ключа через `CAPSULE_AES_KEY_PREVIOUS` и защита списка от повреждённого шифротекста**:
   - Формат шифротекста `[12 байт IV][GCM ciphertext + 16 байт tag]` не изменён: любые существующие записи в БД читаются напрямую.
   - При возникновении `AEADBadTagException` во время расшифровки (`decryptBytes`) и наличии `CAPSULE_AES_KEY_PREVIOUS` автоматически выполняется повторная попытка расшифровки предыдущим ключом.
   - В `GeoUtils.stageText` ошибка расшифровки повреждённой записи логируется предупреждением (только тип исключения, без секретов и данных) и возвращает заглушку `CORRUPTED_DATA_PLACEHOLDER`, не роняя выдачу всего списка капсул.
9. **R1.9 (Аудит 16, 20, 28, 31, 34, 35 + `/ping`): Оптимизация пула БД, кеширование верификатора, логирование и Docker**:
   - `GoogleIdTokenVerifier` и `NetHttpTransport` кешируются как потокобезопасные синглтоны по `webClientId` (`getOrCreateGoogleIdTokenVerifier`).
   - В `DatabaseConfig.buildHikariConfig` установлены `minimumIdle = 0` и `idleTimeout = 60_000L`, а фоновая очистка сессий (`purgeExpiredSessions`) переведена на запуск при старте и раз в 6 часов, чтобы серверная БД Neon Free могла переходить в спящий режим при простое.
   - При конфликте уникальности `@handle` в `updateUserHandle` возвращается `409 Conflict` с текстом `"Этот @ник уже занят"`.
   - Подключён плагин `CallLogging` (логирует только метод, путь и HTTP-статус без заголовков и тел) и логирование стектрейса необработанных ошибок в `StatusPages` (`exception<Throwable>`).
   - Добавлен легковесный публичный `GET /ping` (`"ok"` без обращения к БД), а `/health` сохранён с проверкой соединения БД. Корневой `Dockerfile` унифицирован с `backend/Dockerfile` (`eclipse-temurin:21-jre-jammy`, непривилегированный `appuser`, `HEALTHCHECK`, `-XX:MaxRAMPercentage=75.0`).
10. **R1.10 (Аудит 4, 14, 17, 19, 29): Клиентские исправления навигации, медиаплеера и шаблонов**:
    - Устранён повторный вызов `onOpenTimeline` при открытии капсулы с полки (`CapsulesShelfScreen.kt:275`).
    - В `PhotoMediaCard` и `VoiceMediaCard` добавлен `retryTrigger` в ключ `LaunchedEffect(mediaItem.id, retryTrigger)` для реального перезапуска загрузки по кнопке «Повторить»; в `VoicePlayerHelper` и `VoiceMediaCard` активирована перемотка `Slider` (`seekTo`).
    - В `signInWithGoogle` удалён дублирующийся вызов `remoteRepository.authGoogle` в блоке `catch`.
    - На полке капсул (`CapsulesShelfScreen.kt`) чип кода приглашения отображается только для групповых капсул (`capsule.isGroupCapsule`).
    - В `IdeasScreen.kt` разблокированы шаблоны `photo_archive` (`kind = "PHOTO"`) и `voice_2026` (`kind = "VOICE"`), удалён мёртвый экран `MyCapsulesScreen.kt` (`ROUTE_MY_CAPSULES`) и неиспользуемые методы в `CapsuleViewModel`.
11. **R2.1 (Аудит 3 и Функция 2): Локальные уведомления 2.0 (`AlarmManager`, `BootCompletedReceiver`, 3 канала, тихие часы, годовщина, `EXTRA_CAPSULE_ID`)**:
    - Запрос runtime-разрешения `POST_NOTIFICATIONS` (API 33+) выполняется при включении тумблера уведомлений в `SettingsScreen` и перед запечатыванием первой капсулы в `CreateCapsuleScreen`.
    - Создаются 3 канала уведомлений: `capsule_unlock` (личные капсулы), `capsule_social` (входящие и групповые капсулы), `capsule_anniversary` (годовщина открытия через 1 год).
    - Уведомления о наступлении времени открытия планируются через `AlarmManager`: при `canScheduleExactAlarms() == true` (или API < 31) используется `setExactAndAllowWhileIdle`, иначе — безопасный fallback на `setWindow` (~10 минут) без запроса специального разрешения `SCHEDULE_EXACT_ALARM`.
    - Планы уведомлений сериализуются в `SharedPreferences("capsule_unlock_plans_prefs")` и автоматически перерегистрируются при перезагрузке или обновлении приложения через `BootCompletedReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`).
    - Уведомление о годовщине (ровно через 1 год после открытия капсулы) планируется через `WorkManager` (`CapsuleAnniversaryWorker`).
    - Реализован режим «Тихие часы (23:00–08:00)» (по умолчанию выключен): уведомления, попадающие в интервал 23:00–08:00, переносятся на 08:00 утра.
    - В `MainActivity` (`onCreate` и `onNewIntent`) обрабатывается `EXTRA_CAPSULE_ID`: целевой ID сохраняется в `CapsuleViewModel`, ожидает завершения авторизации и разблокировки PIN, при необходимости подгружает список капсул с сервера и открывает экран таймлайна (либо показывает понятное сообщение в Snackbar, если капсула удалена или недоступна).

