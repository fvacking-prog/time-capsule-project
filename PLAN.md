# План развития и полный аудит проекта «Time Capsule 2.0»

## 1) Краткое резюме
1. Проведён сквозной аудит 100% кода `backend` (Ktor + Exposed + PostgreSQL/H2 + AES-256-GCM) и `android-client` (Kotlin + Jetpack Compose + Material 3 Tokyo Night).
2. Найдено **36 проблем** (4 Critical, 11 High, 13 Medium, 8 Low), влияющих на память сервера Render Free (512 МБ), безопасность сессий/капсул и работу уведомлений/медиа на Android 12–15.
3. Главный серверный блокер: выгрузка зашифрованных BLOB-байтов медиа в `buildCapsuleDto` на каждый запрос списка капсул и проблема `4 + 5×N` SQL-запросов (риск `OutOfMemoryError` на Render Free).
4. Главный продуктовый баг сервера: гео-капсула со статусом `OPENED` повторно блокирует текст и медиа через 60 секунд после чекина (`allParticipantsNearbyAndRecent` сбрасывается).
5. Главные блокеры клиента: никогда не запрашивается runtime-разрешение `POST_NOTIFICATIONS` (Android 13+), не обрабатывается `EXTRA_CAPSULE_ID` из уведомлений в `MainActivity`, происходит двойная навигация при открытии капсулы с полки.
6. Выявлены дыры приватности и безопасности: `DELETE /api/v1/me/data` не удаляет запись из таблицы `Users`, инвайт-коды создаются даже для личных капсул (позволяя войти по коду), `AuthRateLimiter` уязвим к спуфингу `X-Forwarded-For` и утечке памяти.
7. Спроектировано **16 существующих улучшений** и **20 новых современных функций**, отсортированных по соотношению «польза / трудозатраты» с учётом бесплатных лимитов Render Free и Neon Free.
8. Разработана архитектура уведомлений (локальные WorkManager + точные будильники + опциональный FCM), экран «Моя статистика» на чистом Compose Canvas, Glance-виджет, биометрия, Room-кеш и экспорт карточек капсул в соцсети.
9. Весь объём работ разбит на **6 релизов (R1–R6)** по 1–3 дня работы агента и отдельный блок из **8 «быстрых побед»** (до 2 часов каждая, суммарно ~9,5 ч).
10. В разделах 6–8 сформулированы Топ-5 приоритетов, анти-рекомендации («чего делать не стоит»), 8 точечных вопросов для принятия решений пользователем и честные ограничения статического анализа без физического смартфона.

---

## 2) Таблица аудита (ШАГ 1)

| № | файл:строка | Серьёзность | Категория | Проблема | Как исправить | Оценка (ч) |
|---:|---|---|---|---|---|---:|
| 1 | [CapsuleRoutes.kt:1270](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1270-L1281) | **Critical** | Производительность / OOM | В `buildCapsuleDto` вызывается `CapsuleMedia.selectAll()`, что выгружает из БД колонку `encryptedBytes` (BLOB до 1,8 МБ на капсулу) при каждом вызове `GET /api/v1/capsules`, `POST /check-in`, `POST /open`, хотя в DTO нужны только метаданные. На Render Free (512 МБ RAM) список из 25 капсул с медиа читает ~45 МБ из Neon в кучу JVM на один запрос. | Заменить `selectAll()` на проекцию без BLOB: `CapsuleMedia.select(CapsuleMedia.id, CapsuleMedia.kind, CapsuleMedia.mime, CapsuleMedia.sizeBytes, CapsuleMedia.durationMs).where { CapsuleMedia.capsuleId eq capsuleId }`. | 0.5 |
| 2 | [CapsuleRoutes.kt:642](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L642-L666), [CapsuleRoutes.kt:1210](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1210-L1268) | **Critical** | Ошибки и крайние случаи | Повторная блокировка уже открытой гео-капсулы: после `POST /api/v1/capsules/{id}/open` участнику ставится `openedAt`, статус становится `OPENED`, но через 60 секунд (`RECENT_THRESHOLD_MS`) `allParticipantsNearbyAndRecent` снова становится `false`. В результате `isFullyUnlocked` падает в `false`, текст этапов снова заменяется на `[ЗАШИФРОВАНО AES-256-GCM]`, а скачивание медиа возвращает `409 Conflict`. | Если у текущего участника `myOpenedAt != null`, считать гео-условие для него уже выполненным навсегда (`val geoSatisfied = myOpenedAt != null \|\| allParticipantsNearbyAndRecent`) как в `buildCapsuleDto`, так и в `GET /media/{mediaId}`. | 1.0 |
| 3 | [CapsuleUnlockWorker.kt:127](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/CapsuleUnlockWorker.kt#L127-L145), [MainActivity.kt:96](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/MainActivity.kt#L96-L124) | **Critical** | Ошибки / Уведомления | 1) На Android 13–15 (`targetSdk = 35`) приложение нигде не запрашивает runtime-разрешение `POST_NOTIFICATIONS` — `CapsuleUnlockNotificationWorker` тихо выходит на строке 132. 2) `MainActivity` не читает `intent.getStringExtra(EXTRA_CAPSULE_ID)` ни в `onCreate`, ни в `onNewIntent` — нажатие на уведомление не открывает капсулу. | 1) Добавить `rememberLauncherForActivityResult(RequestPermission())` при включении тумблера уведомлений и перед первым запечатыванием капсулы. 2) Обрабатывать `EXTRA_CAPSULE_ID` в `MainActivity.onCreate`/`onNewIntent` с переходом на `ROUTE_TIMELINE`. | 1.5 |
| 4 | [CapsulesShelfScreen.kt:275](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsulesShelfScreen.kt#L275-L278), [MainActivity.kt:498](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/MainActivity.kt#L498-L503) | **Critical** | Ошибки / Навигация | Двойная навигация при нажатии «Открыть капсулу» на полке: в `CapsulesShelfScreen` вызываются подряд `onOpenCapsule(capsule.id)` и `onOpenTimeline(capsule.id)`, а `onOpenCapsule` в `MainActivity` по завершении сетевого запроса ещё раз вызывает `navController.navigate(ROUTE_TIMELINE)`. В стек кладётся два экрана `timeline`, первый открывается до ответа сервера. | Убрать прямой вызов `onOpenTimeline(capsule.id)` из `onOpenCapsule`-колбэка в `CapsulesShelfScreen.kt:277` (оставить навигацию только в `onSuccess` внутри `MainActivity.kt:500` либо использовать `launchSingleTop = true`). | 0.5 |
| 5 | [CapsuleRoutes.kt:290](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L290-L310) | **High** | Безопасность / Приватность | Эндпоинт `DELETE /api/v1/me/data` («Удалить мои данные») удаляет капсулы, сессии и блокировки, но **не удаляет строку пользователя в таблице `Users`** (`email`, `googleSub`, `handle`, `displayName`, `photoUrl` остаются в БД навсегда). | После удаления зависимых записей выполнять `Users.deleteWhere { Users.id eq userId }` внутри той же транзакции. | 0.5 |
| 6 | [CapsuleRoutes.kt:148](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L148-L178), [CapsuleRoutes.kt:516](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L516-L520) | **High** | Безопасность / Авторизация | 1) Инвайт-код (`inviteCode`) генерируется для **всех** капсул, включая личные («Для себя»), и `POST /capsules/join` позволяет любому пользователю, угадавшему 6 символов, вступить в личную капсулу, превратив её в групповую (`isGroupCapsule = true`). 2) При промахе по индексу строка 149 делает `Capsules.selectAll()` (full scan всей таблицы в память). 3) В `joinCapsuleByInviteCode` и `canUserAccessCapsule` проверяется только блокировка `userId -> creatorId`, но не `creatorId -> userId`. | 1) Генерировать `inviteCode` только для `isGroupCapsule == true` и разрешать `join` только если `row[Capsules.isGroupCapsule] == true`. 2) Нормализовать `inviteCode.trim().uppercase()` и искать строго по `Capsules.inviteCode eq code` без `selectAll()`. 3) Проверять двустороннюю блокировку `isEitherUserBlocked(userId, creatorId)`. | 1.5 |
| 7 | [CapsuleRoutes.kt:183](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L183-L205), [CapsuleRoutes.kt:224](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L224-L226) | **High** | Безопасность / DoS | 1) `AuthRateLimiter` доверяет первому IP из `X-Forwarded-For`, что позволяет обойти лимит подстановкой случайного заголовка `X-Forwarded-For`. 2) Карта `attempts: ConcurrentHashMap<String, MutableList<Long>>` никогда не удаляет ключи — спам заголовками вызовет утечку памяти (`OutOfMemoryError`). 3) Нет rate-limit на `/api/v1/capsules/join`, `/api/v1/me/handle` и загрузку медиа. | Брать последний прокси-доверенный IP (или `remoteHost`), очищать пустые ключи `attempts.entries.removeIf { ... }` при превышении размера карты (например, `> 5000` ключей) и подключить лимитер к `/capsules/join` и `/auth/demo`. | 1.5 |
| 8 | [CapsuleRoutes.kt:395](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L395-L399), [CapsuleRoutes.kt:1141](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1141-L1305) | **High** | Производительность / БД | Проблема N+1 запросов в `GET /api/v1/capsules`: `getVisibleCapsuleIds` делает 4 запроса, после чего в цикле для каждого `id` вызывает `buildCapsuleDto`, выполняющий ещё 5 SQL-запросов (`Capsules`, `Users`, `CapsuleParticipants`, `CapsuleStages`, `CapsuleMedia`) = `4 + 5×N` запросов на каждый рефреш списка. | Загружать капсулы, этапы, участников и метаданные медиа батчем через `inList(ids)` за 4 константных запроса (`O(1)` запросов вместо `O(N)`). | 2.5 |
| 9 | [Tables.kt:21](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Tables.kt#L21-L90) | **High** | Производительность / БД | В схеме Exposed отсутствуют индексы по внешним ключам и полям фильтрации: `Capsules.creatorId`, `CapsuleStages.capsuleId`, `CapsuleParticipants.userId`, `CapsuleMedia.capsuleId`, `Sessions.userId`, `Sessions.expiresAt`, `Blocks.blockedUserId`. По мере роста таблиц в Neon каждый запрос списка и проверки сессии деградирует до Seq Scan. | Добавить композитные и одиночные `index(...)` в `Tables.kt` (с автосозданием через `SchemaUtils.createMissingTablesAndColumns` / Flyway). | 1.0 |
| 10 | [CapsuleRoutes.kt:975](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L975-L995) | **High** | Производительность / БД | `searchUsersByQuery` выполняет `Users.selectAll().where { (Users.id neq currentUserId) and Users.handle.isNotNull() }.toList()`, загружая **всю таблицу пользователей** в память JVM на каждое нажатие клавиши при поиске `@handle`. | Перенести фильтрацию в SQL: `Users.selectAll().where { (Users.id neq currentUserId) and (Users.handle like "$q%") }.limit(30)` и исключать заблокированных пользователей в SQL. | 1.0 |
| 11 | [Application.kt:136](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Application.kt#L136-L145), [CapsuleRoutes.kt:417](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L417-L420) | **High** | Безопасность / DoS | Проверка лимита размера тела запроса (10 МБ глобально и 2,5 МБ на медиа) опирается только на заголовок `Content-Length`. При отправке `Transfer-Encoding: chunked` заголовок отсутствует, и `call.receiveMultipart()` / `readBytes()` может вычитать поток произвольного размера до OOM. | Читать `InputStream` в `receiveMediaUpload` через `readNBytes(maxBytes + 1)` с жёстким прерыванием при превышении лимита, а также задать `bodyLimit` на уровне движка Netty/Ktor. | 1.0 |
| 12 | [CapsuleRoutes.kt:1240](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1240-L1241), [CreateCapsuleScreen.kt:1082](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CreateCapsuleScreen.kt#L1082-L1188) | **High** | Ошибки / Логика этапов | 1) На сервере `isFullyUnlocked = stages.isNotEmpty() && stages.any { it.isUnlocked } && ...` — если открылся 1-й этап из 3, вся капсула помечается `isFullyUnlocked = true` (хотя 2-й и 3-й этапы ещё в будущем). 2) На клиенте методы `addCreateStage`/`removeCreateStage` в `CapsuleViewModel` передаются в `CreateCapsuleScreen`, но в UI редактируется только `stages.firstOrNull()` — кнопки добавления 2-го и 3-го этапов нет. | 1) Разделить `hasUnlockedStage = stages.any { it.isUnlocked }` (доступ к открытым этапам и медиа) и `isFullyUnlocked = stages.all { it.isUnlocked }`. 2) В `CreateCapsuleScreen` подключить UI-список этапов к уже готовым колбэкам `onAddStage`/`onRemoveStage`. | 2.0 |
| 13 | [ApiConfig.kt:18](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/ApiConfig.kt#L18-L21), [UserPreferences.kt:39](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/UserPreferences.kt#L39-L71), [MainActivity.kt:162](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/MainActivity.kt#L162-L168) | **High** | Безопасность клиента | 1) Bearer-токен сессии хранится в открытом виде в `preferencesDataStore`. 2) 4-значный PIN хешируется одним проходом `SHA-256(salt:pin)` без ограничения числа попыток ввода (`EnterPinDialog` можно перебирать бесконечно). 3) При открытом `EnterPinDialog` позади диалога рендерится основной экран с названиями капсул, и окно не защищено `FLAG_SECURE` в меню недавних приложений. | 1) Шифровать `session_token` ключом из Android Keystore (`AES/GCM/NoPadding`). 2) Перевести PIN на `PBKDF2WithHmacSHA256` (60 000 итераций, с прозрачной миграцией) + добавить кулдаун 30 с после 5 неверных попыток. 3) Пока `isPinLocked == true`, рендерить полноэкранный экран блокировки вместо `Scaffold`. | 2.5 |
| 14 | [CapsuleStagesTimeline.kt:219](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsuleStagesTimeline.kt#L219-L230), [CapsuleStagesTimeline.kt:268](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsuleStagesTimeline.kt#L268-L271), [CapsuleStagesTimeline.kt:354](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsuleStagesTimeline.kt#L354-L366) | **High** | Ошибки / UI медиа | В `PhotoMediaCard` и `VoiceMediaCard` загрузка запускается через `LaunchedEffect(mediaItem.id)`. При ошибке сети кнопка «Повторить» выставляет `isLoading = true; hasError = false`, но ключ `mediaItem.id` не меняется, поэтому `LaunchedEffect` **не перезапускается** и карточка зависает в бесконечном спиннере. | Добавить счётчик `var retryTrigger by remember { mutableIntStateOf(0) }`, передавать `LaunchedEffect(mediaItem.id, retryTrigger)` и в `onClick` кнопки повтора делать `retryTrigger++`. | 0.5 |
| 15 | [CryptoUtils.kt:21](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CryptoUtils.kt#L21-L97) | **High** | Релиз и эксплуатация | Формат шифротекста `IV (12 байт) + Ciphertext + Tag` не содержит идентификатора версии ключа, а при ошибке расшифровки `decryptBytes` выбрасывает необработанный `AEADBadTagException`. При ротации `CAPSULE_AES_KEY` любой вызов `GET /api/v1/capsules` упадёт с `500 Internal Server Error` для всех пользователей. | Добавить поддержку `CAPSULE_AES_KEY_PREVIOUS` (fallback при расшифровке) и префикс версии ключа `v1:` для новых записей, а в `buildCapsuleDto` перехватывать `GeneralSecurityException`, возвращая безопасную заглушку вместо падения всего списка. | 1.5 |
| 16 | [CapsuleRoutes.kt:789](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L789-L794) | **Medium** | Производительность / Сеть | В `verifyGoogleIdToken` на каждый запрос входа создаётся новый экземпляр `GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance()).build()`, что сбрасывает кеш публичных сертификатов Google и делает синхронный HTTPS-запрос к Google на каждый логин. | Вынести `NetHttpTransport`, `GsonFactory` и кешируемый по `webClientId` экземпляр `GoogleIdTokenVerifier` на уровень синглтона файла. | 0.5 |
| 17 | [CapsuleViewModel.kt:337](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/viewmodel/CapsuleViewModel.kt#L337-L345) | **Medium** | Ошибки / Сеть | В `signInWithGoogle` после того, как `remoteRepository.authGoogle(idToken)` уже выполнил 4 попытки через `ColdStartRetry`, блок `catch` при любой ошибке (включая `401`/`400`) вызывает `remoteRepository.authGoogle(idToken)` **второй раз** (ещё до 4 попыток = 8 запросов подряд). | Удалить дублирующий вызов `remoteRepository.authGoogle(idToken)` внутри `catch` в `CapsuleViewModel.kt:340`, оставив только показ понятного сообщения об ошибке. | 0.5 |
| 18 | [AndroidManifest.xml:23](file:///Users/ghost/time-capsule-project/android-client/app/src/main/AndroidManifest.xml#L23), [CreateCapsuleScreen.kt:183](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CreateCapsuleScreen.kt#L183-L202) | **Medium** | Поворот экрана / Убийство процесса | 1) В манифесте жёстко задан `android:screenOrientation="portrait"`, что блокирует альбомный режим на планшетах/складных устройствах (warning в Android 12–15). 2) В `CreateCapsuleScreen` переменная `pendingCameraUri` хранится в `remember` вместо `rememberSaveable`, а состояние черновика в `CapsuleViewModel` не сохраняется в `SavedStateHandle`: если ОС выгрузит процесс во время съёмки внешней камерой, фото и черновик капсулы теряются. | 1) Снять жёсткий `screenOrientation="portrait"`. 2) Сохранять строку `pendingCameraUri` через `rememberSaveable`, а ключевые поля черновика и `selectedCapsuleId` синхронизировать с `SavedStateHandle`. | 2.0 |
| 19 | [CapsulesShelfScreen.kt:631](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsulesShelfScreen.kt#L631-L639) | **Medium** | Безопасность / UX | На карточке полки бейдж `Код: ABC123` отображается для **каждой** капсулы (включая личные капсулы «Для себя» и входящие персональные капсулы), загромождая карточку и провоцируя утечку кода доступа. | Отображать `inviteCode` только если `capsule.isGroupCapsule && !capsule.inviteCode.isNullOrBlank()`. | 0.5 |
| 20 | [DatabaseConfig.kt:67](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/DatabaseConfig.kt#L67-L90), [Tables.kt:80](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Tables.kt#L80-L87) | **Medium** | Производительность / Память | 1) Хикари-пул `maximumPoolSize = 5` с `isAutoCommit = false` без фоновой очистки таблицы `Sessions`: истёкшие сессии (`expiresAt < now`) удаляются только точечно, когда сам протухший токен присылает запрос (`CapsuleRoutes.kt:62`), а брошенные сессии накапливаются в БД бесконечно. | Добавить периодическую фоновую корутину раз в 6 часов (и при старте сервера): `Sessions.deleteWhere { Sessions.expiresAt lessEq System.currentTimeMillis() }`. | 0.5 |
| 21 | [CapsuleRoutes.kt:551](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L551-L595) | **Medium** | Ошибки / Утечка в БД | При создании капсулы с фото/аудио сначала создаётся капсула (`POST /api/v1/capsules`), затем отдельными запросами грузятся файлы (`POST /media`). Если загрузка медиа не удалась и пользователь нажал «Отмена» в диалоге повтора ([CreateCapsuleScreen.kt:650](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CreateCapsuleScreen.kt#L650-L654)), капсула уже остаётся запечатанной на сервере без медиа, но пользователь остаётся на форме создания и при повторном нажатии создаёт дубликат капсулы. | При отмене дозагрузки медиа переводить пользователя на полку с предупреждением «Капсула сохранена без вложений» либо поддержать удаление/дозагрузку медиа для своей капсулы в течение 5 минут после создания. | 1.0 |
| 22 | [CreateCapsuleScreen.kt:191](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CreateCapsuleScreen.kt#L191-L202), [MediaHelper.kt:126](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/MediaHelper.kt#L126-L133) | **Medium** | Производительность / Диск | Временные файлы камеры `camera_capture_*.jpg`, сжатые `capsule_photo_*.jpg` и скачанные для просмотра `view_media_*` в `cacheDir` не очищаются при старте приложения или удалении вложения, постепенно раздувая кеш приложения. | Добавить `MediaHelper.cleanupStaleCacheFiles(context)` (удаление временных файлов старше 24 часов) при запуске `CapsuleViewModel.init`. | 0.5 |
| 23 | [LocationProvider.kt:66](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/LocationProvider.kt#L66-L137) | **Medium** | Батарея и геолокация | `LocationProvider.getCurrentLocation()` вызывается каждые 5 секунд на экране Радара, и при отсутствии свежего `lastKnownLocation` подписывается одновременно на `GPS_PROVIDER` и `NETWORK_PROVIDER`. На улице `lastKnownLocation` может быть многочасовой давности — в `selectBestLocation` нет проверки возраста локации (`location.time`), поэтому приложение может отправлять устаревшие координаты вместо ожидания нового фикса. | В `selectBestLocation` отбрасывать `lastKnownLocation` старше 2 минут (`System.currentTimeMillis() - location.time > 120_000L`), чтобы триггерить актуальный GPS/Network фикс. | 0.5 |
| 24 | [Capsule3DView.kt:153](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/components/Capsule3DView.kt#L153-L237), [RadarScreen.kt:238](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/RadarScreen.kt#L238-L290) | **Medium** | Производительность / Compose | 1) В `Capsule3DView` запущены 5 параллельных `animateFloat` в `rememberInfiniteTransition`, которые тикают на 60 FPS даже когда пользователь читает нижнюю часть `HomeScreen`. 2) В `Capsule3DView.kt:712` и `815` внутри `DrawScope` на каждом кадре создаются новые экземпляры `Path()`. | 1) Переиспользовать `val reusablePath = remember { Path() }` (`reusablePath.reset()`). 2) Передавать state-провайдеры `() -> Float` в `Modifier.drawWithCache` для чтения анимаций только в фазе отрисовки без рекомпозиции `Capsule3DView`. | 1.5 |
| 25 | [backend/.../ModelsDto.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/ModelsDto.kt#L1-L160), [android-client/.../ModelsDto.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/timecapsule/shared/ModelsDto.kt#L1-L160) | **Medium** | Архитектура и качество | Полное дублирование файла `ModelsDto.kt` (160 строк) между `backend` и `android-client`. Любое изменение контракта требует ручной синхронизации двух файлов. | Подключить единый каталог исходников DTO через `sourceSets` в Gradle (или Gradle Composite Build `shared-dto`) либо добавить автопроверку `diff` в Gradle-таску `check`. | 1.0 |
| 26 | [CapsuleViewModel.kt:1](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/viewmodel/CapsuleViewModel.kt#L1-L1833) | **Medium** | Архитектура и качество | Божественный класс `CapsuleViewModel` (1833 строки) совмещает авторизацию, профиль `@handle`, черновик создания капсулы, диктофон, медиаплеер, пин-код, локальные уведомления, обновления APK и гео-радар. Это затрудняет модульное тестирование и сопровождение. | Выделить доменные контроллеры/делегаты (`AuthStateDelegate`, `CreateCapsuleDraftHolder`, `PinSecurityManager`) без ломки внешнего `uiState`. | 3.0 |
| 27 | [CapsulesShelfScreen.kt:445](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsulesShelfScreen.kt#L445-L455), [CapsuleStagesTimeline.kt:272](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsuleStagesTimeline.kt#L272-L275), [Capsule3DView.kt:582](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/components/Capsule3DView.kt#L582-L601) | **Medium** | Доступность (TalkBack / Touch) | 1) Кнопка меню карточки на полке (`CapsulesShelfScreen.kt:445`) имеет `Modifier.size(28.dp)` (меньше минимума 48×48 dp) и `contentDescription = null`. 2) Интерактивные кнопки Play/Pause, Retry и удаления чипов имеют `contentDescription = null`. 3) Интерактивный `Canvas` 3D-капсулы и Гео-радара не имеют `semantics { contentDescription = ... }`. 4) В `VoiceMediaCard` (`CapsuleStagesTimeline.kt:460`) `Slider` всегда `enabled = false` — нельзя перемотать голосовое послание. | Добавить `contentDescription` из `strings.xml` для всех интерактивных иконок и Canvas, увеличить touch target кнопки меню до 48 dp и включить перемотку `mediaPlayer.seekTo(...)` в `Slider` голосового плеера. | 1.5 |
| 28 | [Dockerfile:1](file:///Users/ghost/time-capsule-project/Dockerfile#L1-L23), [backend/Dockerfile:1](file:///Users/ghost/time-capsule-project/backend/Dockerfile#L1-L33), [docker-compose.yml:5](file:///Users/ghost/time-capsule-project/docker-compose.yml#L5) | **Medium** | Релиз и эксплуатация | В проекте два разных `Dockerfile`: корневой `Dockerfile` использует JRE 17, работает от `root` и не имеет `HEALTHCHECK`, тогда как `backend/Dockerfile` использует JRE 21, непривилегированного `appuser` и `HEALTHCHECK`. При этом `docker-compose.yml` ссылается на устаревший корневой `Dockerfile`. | Унифицировать корневой `Dockerfile` с `backend/Dockerfile` (JRE 21, `appuser`, `HEALTHCHECK`, флаг `-XX:MaxRAMPercentage=75.0` для лимита 512 МБ на Render Free). | 0.5 |
| 29 | [MyCapsulesScreen.kt:1](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/MyCapsulesScreen.kt#L1-L37), [MainActivity.kt:94](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/MainActivity.kt#L94), [IdeasScreen.kt:127](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/IdeasScreen.kt#L127-L148) | **Low** | Мёртвый код / Устаревшие заглушки | 1) Экран `MyCapsulesScreen.kt` и маршрут `ROUTE_MY_CAPSULES` нигде не вызываются (используется `CapsulesShelfScreen`). 2) В `IdeasScreen.kt:127-148` карточки «Фото-архив момента» и «Голосовое в 2026» помечены `isComingSoon = true` («Скоро») и заблокированы, хотя фото и голос уже полностью работают! 3) В `CapsuleViewModel` не используются `openPinPromptDialog`, `updateVoiceRecordingState`, `onLocationPermissionDenied`. | 1) Разблокировать шаблоны `photo_archive` и `voice_2026` в `IdeasScreen.kt` (`isComingSoon = false`). 2) Удалить мёртвый маршрут `ROUTE_MY_CAPSULES` и неиспользуемые методы. | 0.5 |
| 30 | [CreateCapsuleScreen.kt:524](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CreateCapsuleScreen.kt#L524-L535), [HomeScreen.kt:256](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/HomeScreen.kt#L256-L259), [PinLockDialog.kt:73](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/PinLockDialog.kt#L73-L75) | **Low** | Строки и локализация | Часть пользовательских строк захардкожена прямо в Kotlin-коде в обход `strings.xml` (тексты диалога создания капсулы с медиа, кнопки PIN-диалога, подсказки на Главной, сообщения ошибок в `CapsuleViewModel.kt:466,683,924,1428,1706`). | Вынести все захардкоженные русские строки в `res/values/strings.xml`. | 1.0 |
| 31 | [CapsuleRoutes.kt:355](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L355-L370) | **Low** | Ошибки и крайние случаи | В `PUT /api/v1/me/handle` при одновременной отправке одинакового `@handle` двумя пользователями проверка `exists` проходит у обоих, а затем второй поток падает с `ExposedSQLException` (нарушение `uniqueIndex` в БД) и получает `500 Internal Server Error` вместо `409 Conflict`. | Перехватывать `ExposedSQLException` при `Users.update` в `PUT /api/v1/me/handle` и возвращать `409 Conflict ("Этот @ник уже занят")`. | 0.5 |
| 32 | [AppUpdateManager.kt:129](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/AppUpdateManager.kt#L129-L176) | **Low** | Безопасность / Сеть | В `AppUpdateManager.downloadApk` используется `HttpURLConnection` с `instanceFollowRedirects = true`. Если сервер редиректнет с `https://` на `http://`, стандартный `HttpURLConnection` не переключится между протоколами (упадёт), а при ручных редиректах не проверяется, что целевой хост остаётся доверенным. Кроме того, старые APK в `cacheDir/updates` удаляются только перед новым скачиванием, но не после успешной установки. | Очищать `cacheDir/updates/timecapsule-update.apk` при старте приложения, если `BuildConfig.VERSION_CODE` уже обновился. | 0.5 |
| 33 | [Theme.kt:23](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/theme/Theme.kt#L23-L35) | **Low** | Доступность (Контраст / Шрифт) | В `TokyoNightColorScheme` цвет `onSurfaceVariant` не задан явно (берётся дефолтный Material 3), а в ряде карточек используется `TokyoOnSurface.copy(alpha = 0.55f)` на фоне `#24283B`, что даёт контраст ~3.4:1 (ниже нормы WCAG AA 4.5:1 для мелкого текста). | Явно задать `onSurfaceVariant = TokyoOnSurface` (`#A9B1D6`, контраст 6.8:1 на `#1A1B26` и 5.1:1 на `#24283B`) и не снижать `alpha` текста ниже `0.78f`. | 0.5 |
| 34 | [Application.kt:61](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Application.kt#L61-L157), [logback.xml:1](file:///Users/ghost/time-capsule-project/backend/src/main/resources/logback.xml#L1-L11) | **Low** | Мониторинг и логирование | Отсутствует плагин `CallLogging` и `RequestId` в Ktor, а в `StatusPages` при `exception<Throwable>` (`Application.kt:153`) ошибка перехватывается и возвращает `500`, **вообще не логируя стектрейс** (`environment.log.error(...)` не вызывается!). При падении в продакшене в логах Render будет пусто. | Добавить `call.application.environment.log.error("Unhandled error on ${call.request.uri}", cause)` в `StatusPages` и подключить `CallLogging` (без логирования заголовка `Authorization` и тел запросов). | 0.5 |
| 35 | [libs.versions.toml:1](file:///Users/ghost/time-capsule-project/android-client/gradle/libs.versions.toml#L1-L30), [backend/build.gradle.kts:19](file:///Users/ghost/time-capsule-project/backend/build.gradle.kts#L19-L41) | **Low** | Безопасность / Зависимости | В `backend/build.gradle.kts` используется `com.google.api-client:google-api-client:2.2.0` (2023 г., транзитивные зависимости Guava/HttpClient устарели) и `h2:2.2.224`. | Обновить `google-api-client` до `2.7.0`, `postgresql` до `42.7.4`, `logback-classic` до `1.4.14`. | 0.5 |
| 36 | [BackendUnitTests.kt:1](file:///Users/ghost/time-capsule-project/backend/src/test/kotlin/com/timecapsule/server/BackendUnitTests.kt#L1-L1937), [UnlockAndConfigUnitTest.kt:1](file:///Users/ghost/time-capsule-project/android-client/app/src/test/java/com/example/cyberpunk/UnlockAndConfigUnitTest.kt#L1-L286) | **Low** | Тестируемость и покрытие | На бэкенде отличное покрытие (37 тестов), но на Android-клиенте нет юнит-тестов для `CapsuleViewModel`, `UserPreferences` (PIN) и `MediaHelper`. | Добавить JVM-тесты для `UserPreferences` (хеширование/проверка PIN), фильтрации полок и сценариев `CapsuleViewModel` с `MockCapsuleRepository`. | 2.0 |

---

## 3) Таблица улучшений существующего (ШАГ 2)

| № | Улучшение | Зачем | Затронутые файлы | Трудозатраты (ч) | Риск |
|---:|---|---|---|---:|---|
| 1 | Исключение `encryptedBytes` из SQL-выборки метаданных в `buildCapsuleDto` | Снижает потребление памяти и трафика БД в 50–100 раз при чтении списка капсул; устраняет риск OOM на Render Free (512 МБ). | [CapsuleRoutes.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1270-L1281) | 0.5 | Низкий |
| 2 | Батч-загрузка списка капсул (`O(1)` SQL-запросов вместо `4 + 5×N`) + ленивая расшифровка текста | Ускоряет `GET /api/v1/capsules` на Neon Free с ~600 мс до ~60 мс при 30 капсулах; не тратит CPU на AES-расшифровку текстов в общем списке полки. | [CapsuleRoutes.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L395-L399), [CapsuleRoutes.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1141-L1305) | 2.5 | Средний |
| 3 | Сохранение открытого состояния гео-капсул (`openedAt != null`) и разделение `hasUnlockedStage` / `isFullyUnlocked` | Открытая на локации гео-капсула остаётся доступной для чтения и прослушивания после ухода пользователя с точки; корректно работают многоэтапные капсулы. | [CapsuleRoutes.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L642-L666), [CapsuleRoutes.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1210-L1268), [CapsuleStagesTimeline.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsuleStagesTimeline.kt#L165) | 1.5 | Низкий |
| 4 | Полная цепочка локальных уведомлений: запрос `POST_NOTIFICATIONS` + диплинк `EXTRA_CAPSULE_ID` в `MainActivity` | Делает уведомления о созревании капсул реально работающими на Android 13–15 и сразу открывает нужную капсулу по тапу. | [MainActivity.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/MainActivity.kt#L96-L130), [SettingsScreen.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/SettingsScreen.kt#L388-L414), [CapsuleUnlockWorker.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/CapsuleUnlockWorker.kt#L127-L145) | 1.5 | Низкий |
| 5 | Добавление индексов БД в `Tables.kt` и SQL-поиска `LIKE` с `LIMIT` в `searchUsersByQuery` | Убирает полные сканирования таблиц `Users`, `Capsules`, `CapsuleStages`, `Sessions`; экономит compute-часы Neon Free. | [Tables.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Tables.kt#L21-L90), [CapsuleRoutes.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L975-L995) | 1.5 | Низкий |
| 6 | Защита `joinCapsuleByInviteCode`: инвайт-коды только для групповых капсул, двусторонняя проверка ЧС и rate-limit | Закрывает несанкционированный вход в личные капсулы по коду и защищает от перебора 6-значных кодов и спуфинга `X-Forwarded-For`. | [CapsuleRoutes.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L140-L205), [CapsulesShelfScreen.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsulesShelfScreen.kt#L631-L639) | 2.0 | Низкий |
| 7 | Исправление кнопки «Повторить» в `PhotoMediaCard`/`VoiceMediaCard` + включение перемотки `Slider` в аудиоплеере | Позволяет перезагрузить фото/голос после обрыва связи без перезахода на экран и перематывать длинные голосовые записи. | [CapsuleStagesTimeline.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsuleStagesTimeline.kt#L219-L275), [CapsuleStagesTimeline.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsuleStagesTimeline.kt#L354-L470) | 1.0 | Низкий |
| 8 | Включение UI многоэтапных капсул (до 5 этапов с разными датами) в `CreateCapsuleScreen` | Сервер и `CapsuleViewModel` уже поддерживают список `stages`, но UI позволяет редактировать только 1 этап. Подключение кнопок раскрывает готовую фичу. | [CreateCapsuleScreen.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CreateCapsuleScreen.kt#L1082-L1188) | 1.5 | Низкий |
| 9 | Активация шаблонов «Фото-архив момента» и «Голосовое в 2026» на экране Идей | Убирает плашку «Скоро» с функций, которые уже реализованы в проекте, и сразу предзаполняет тип капсулы с фокусом на медиа. | [IdeasScreen.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/IdeasScreen.kt#L127-L148) | 0.5 | Низкий |
| 10 | Шифрование `session_token` через Android Keystore + `PBKDF2` для PIN + полноэкранный PIN-экран с кулдауном | Защищает сессию и PIN при физическом доступе или бэкапе, скрывает контент капсул за полноэкранным щитом до ввода PIN. | [ApiConfig.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/ApiConfig.kt#L130-L158), [UserPreferences.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/UserPreferences.kt#L39-L71), [MainActivity.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/MainActivity.kt#L162-L168), [PinLockDialog.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/PinLockDialog.kt#L108-L174) | 2.5 | Средний |
| 11 | Механизм безболезненной ротации `CAPSULE_AES_KEY` (версионирование `v1:` + `CAPSULE_AES_KEY_PREVIOUS`) | Позволяет менять ключ шифрования сервера без потери ранее запечатанных капсул и медиафайлов. | [CryptoUtils.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CryptoUtils.kt#L21-L97), [CapsuleRoutes.kt](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1250-L1265) | 1.5 | Низкий |
| 12 | Оптимизация `Capsule3DView` и `RadarScreen` (переиспользование `Path`, пауза бесконечных анимаций вне видимости) | Снижает нагрев и расход батареи на Главной и Радаре, убирает лишние аллокации в `DrawScope`. | [Capsule3DView.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/components/Capsule3DView.kt#L153-L237), [RadarScreen.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/RadarScreen.kt#L238-L290) | 1.5 | Низкий |
| 13 | Сохранение черновика капсулы и `pendingCameraUri` при убийстве процесса и повороте экрана (`SavedStateHandle`) | Пользователь не теряет набранный текст, теги и сделанный камерой снимок, если система выгрузила Activity в фоне. | [CreateCapsuleScreen.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CreateCapsuleScreen.kt#L183-L202), [CapsuleViewModel.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/viewmodel/CapsuleViewModel.kt#L215-L240) | 2.0 | Низкий |
| 14 | Доступность (TalkBack `contentDescription`, touch target 48 dp, повышенный контраст второстепенного текста) | Делает приложение удобным для скринридера TalkBack и улучшает читаемость на ярком солнце. | [CapsulesShelfScreen.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsulesShelfScreen.kt#L445-L455), [Capsule3DView.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/components/Capsule3DView.kt#L582-L601), [Theme.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/theme/Theme.kt#L23-L35), [strings.xml](file:///Users/ghost/time-capsule-project/android-client/app/src/main/res/values/strings.xml) | 1.5 | Низкий |
| 15 | Объединение `ModelsDto.kt` между `backend` и `android-client` через общий Gradle `sourceSet` | Исключает рассинхронизацию моделей при добавлении новых полей и эндпоинтов. | [backend/build.gradle.kts](file:///Users/ghost/time-capsule-project/backend/build.gradle.kts), [android-client/app/build.gradle.kts](file:///Users/ghost/time-capsule-project/android-client/app/build.gradle.kts) | 1.0 | Низкий |
| 16 | Декомпозиция `CapsuleViewModel` на доменные делегаты + покрытие юнит-тестами | Уменьшает связность кода перед добавлением статистики, Room-кеша и новых социальных функций. | [CapsuleViewModel.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/viewmodel/CapsuleViewModel.kt), [UnlockAndConfigUnitTest.kt](file:///Users/ghost/time-capsule-project/android-client/app/src/test/java/com/example/cyberpunk/UnlockAndConfigUnitTest.kt) | 3.0 | Средний |

---

## 4) Описание новых современных функций (ШАГ 3)

Ниже представлены **20 новых функций**, отсортированных по убыванию коэффициента **«польза / трудозатраты»** и сгруппированных с полным разбором обязательных блоков А–Д.

---

### Блок А. Архитектура уведомлений: сравнение вариантов и рекомендация

| Критерий | Вариант 1: Локальные (`WorkManager` + неточные будильники) | Вариант 2: Локальные точные (`AlarmManager.setExactAndAllowWhileIdle` + `WorkManager`) | Вариант 3: Серверные Push (Firebase Cloud Messaging + бэкенд) |
|---|---|---|---|
| **«Капсула готова к открытию»** | Задержка до 15–60 мин в Doze Mode | Точно в минуту открытия (при наличии `SCHEDULE_EXACT_ALARM` / fallback на `setWindow`) | Требует постоянно работающего cron на сервере (не работает, когда Render Free спит) |
| **«Вам прислали капсулу»** | Только при следующей синхронизации / периодическом воркере | Только при периодическом фоновом опросе (раз в 6–12 ч) | **Мгновенно** в момент создания капсулы отправителем (сервер в этот момент уже не спит!) |
| **«Друг рядом с точкой гео-капсулы»** | Невозможно в реальном времени | Невозможно без пуша | **Мгновенно** в момент `POST /check-in` друга (сервер уже разбужен запросом чекина!) |
| **«Годовщина капсулы» (1 год с открытия)** | Отлично подходит (`WorkManager` с расчётом `openedAt + 365d`) | Избыточно (минутная точность не нужна) | Не требуется сервер |
| **Ограничения Android 12–15** | Требует только `POST_NOTIFICATIONS` (API 33+) | На Android 12+ `SCHEDULE_EXACT_ALARM` по умолчанию выключен для не-будильников (нужен `canScheduleExactAlarms()` + fallback на `setWindow(..., 10 мин)` + `BOOT_COMPLETED` ресивер) | Требует `google-services.json`, `firebase-messaging` (+1.5 МБ к APK) и сервисный ключ Firebase в секретах Render |

**Рекомендация и поэтапный путь:**
1. **Этап 1 (базовый, без внешних облаков, в R1/R2):** гибрид `AlarmManager` (с проверкой `canScheduleExactAlarms()` на Android 12–15 и автоматическим fallback на `setWindow` 10 мин без требования спецразрешения) для точного момента «Капсула созрела» + `WorkManager` для «Годовщина капсулы» и `BOOT_COMPLETED` восстановления расписания + 3 отдельных канала уведомлений (`capsule_unlock`, `capsule_social`, `capsule_anniversary`), настройка «Тихие часы (23:00–08:00)» и deep link сразу в `ROUTE_TIMELINE`.
2. **Этап 2 (опциональный, в R5):** лёгкая интеграция FCM **только для событийных пушей в момент активного HTTP-запроса** («Вам прислали капсулу» в `POST /capsules` и «Друг на точке гео-капсулы» в `POST /check-in`). Это **не требует фонового демона на Render Free**, так как пуш отправляется прямо внутри HTTP-транзакции действия другого пользователя.

---

### Полный каталог 20 новых функций (отсортировано по «польза / трудозатраты»)

#### №1. Живой обратный отсчёт на Главной + карточки «Следующее открытие» (Категория Г)
- **Описание для пользователя:** На Главном экране под 3D-капсулой отображается живой неоновый таймер (`ДД : ЧЧ : ММ : СС`) до ближайшей созревающей капсулы и прогресс-бар «выдержки» капсулы (от даты запечатывания до даты открытия).
- **Экран (Tokyo Night):** Плашка из матового стекла (`#24283B`, рамка `TokyoCyan` `#7DCFFF`) с моноширинными цифрами в стиле киберпанк-часов и кнопкой быстрого перехода к капсуле.
- **Сервер и клиент:** На сервере в `CapsuleDto` добавить `createdAt: Long` (уже есть в `Tables.Capsules`, но не передавалось в DTO — [Tables.kt:42](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Tables.kt#L42)). На клиенте в `HomeScreen.kt` добавить `LaunchedEffect(nearestCapsule)` с тиком `delay(1000L)` только при `Lifecycle.State.STARTED`.
- **Новые зависимости:** Нет.
- **Трудозатраты:** **2 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Создать капсулу на +10 минут, убедиться, что на Главной тикает посекундный таймер и растёт полоса прогресса.

#### №2. Умные локальные уведомления 2.0: каналы, точный таймер Android 12–15, «Годовщина капсулы», тихие часы и Deep Link (Категория А)
- **Описание для пользователя:** Уведомления приходят точно в минуту созревания капсулы (даже после перезагрузки телефона), напоминают о годовщине открытия спустя год, уважают ночной режим («Не беспокоить с 23:00 до 08:00») и по нажатию открывают саму капсулу.
- **Экран (Tokyo Night):** В `SettingsScreen` карточка «Уведомления» с переключателями «Открытие капсул», «Годовщины» и «Тихие часы (23:00–08:00)» + индикатор статуса системного разрешения Android 13+.
- **Сервер и клиент:** Сервер не меняется. На клиенте: расширить `CapsuleUnlockWorker.kt` (добавить `AlarmManager` с `canScheduleExactAlarms()` и fallback на `setWindow`, `BootCompletedReceiver` в `AndroidManifest.xml`, 3 канала `NotificationChannel`, обработку `EXTRA_CAPSULE_ID` в `MainActivity.onCreate`/`onNewIntent` и планировщик годовщин `openedAt + 365 дней`).
- **Новые зависимости:** Нет.
- **Трудозатраты:** **4.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Запечатать капсулу на +2 минуты, свернуть приложение, получить пуш, нажать на него и сразу попасть на экран `CapsuleStagesTimeline` этой капсулы.

#### №3. Карточка-картинка открытой капсулы для соцсетей + системный Share Sheet (Категории В, Г)
- **Описание для пользователя:** После открытия капсулы пользователь может нажать «Поделиться карточкой» — приложение сгенерирует стильную неоновую PNG-открытку в стиле Tokyo Night («Запечатано в 2025 → Открыто в 2026», заголовок, цитата текста, `@handle` автора) и откроет системное меню отправки в Telegram / Instagram / WhatsApp.
- **Экран (Tokyo Night):** В `CapsuleStagesTimeline` кнопка «Поделиться артефактом» с предпросмотром сгенерированной киберпанк-карточки (тёмный фон `#1A1B26`, неоновая рамка `#BB9AF7`, водяной знак `TIME CAPSULE 2.0`).
- **Сервер и клиент:** Сервер не меняется. На клиенте: рендеринг `Bitmap` (1080×1350 px) на `android.graphics.Canvas`, сохранение в `cacheDir/shares/` и отправка через уже настроенный `FileProvider` (`Intent.ACTION_SEND`, `image/png`).
- **Новые зависимости:** Нет.
- **Трудозатраты:** **3 ч** | **Риск:** Низкий (приватность: генерируется локально на устройстве только по явному нажатию пользователя).
- **Что сделать вручную:** Ничего.
- **Как проверить:** Открыть созревшую капсулу, нажать «Поделиться карточкой», проверить PNG-изображение в системном Share Sheet.

#### №4. «Ответ в будущее» (Reply-капсула в один тап) и быстрые реакции получателя (Категория Г)
- **Описание для пользователя:** Открыв полученную от друга капсулу, получатель может поставить неоновую реакцию (`❤️`, `🔥`, `🥹`, `🚀`, `🌌`), которую увидит автор, или нажать «Ответить в будущее» — откроется экран создания капсулы с уже подставленным `@handle` автора, тегом `#ответ` и ссылкой на исходную капсулу.
- **Экран (Tokyo Night):** Внизу открытой `CapsuleStagesTimeline` ряд из 5 неоновых пилюль-реакций с подсветкой выбранной и кнопка `TokyoCyan` «Ответить капсулой в будущее».
- **Сервер и клиент:** На сервере: колонка `reaction: varchar(16).nullable()` в `CapsuleParticipants` и `replyToCapsuleId: varchar(64).nullable()` в `Capsules`, лёгкий эндпоинт `POST /api/v1/capsules/{id}/reaction`. На клиенте: ряд реакций и предзаполнение `CreateCapsuleFormState` по `replyToCapsule`.
- **Новые зависимости:** Нет.
- **Трудозатраты:** **3.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Открыть входящую капсулу, выбрать реакцию `🔥`, нажать «Ответить в будущее» и убедиться, что получатель `@handle` и заголовок `Re: ...` заполнены автоматически.

#### №5. Экран «Моя статистика» на Compose Canvas (Категория Б)
- **Описание для пользователя:** Наглядная аналитика личной истории памяти: сколько капсул запечатано, открыто, получено и отправлено, текущая серия месяцев активности, столбчатый график по месяцам, GitHub-подобная тепловая карта (heatmap), кольцевая диаграмма по тегам и линия времени «Будущие открытия».
- **Экран (Tokyo Night):** Отдельный экран (переход с Главной и из Настроек):
  1. Сетка 2×2 неоновых KPI-карточек (Запечатано `TokyoPurple`, Открыто `TokyoGreen`, Получено `TokyoCyan`, Отправлено `TokyoBlue`) + бейдж серии («Серия: N мес.»).
  2. Сетка тепловой карты за 6 месяцев (`Canvas` с округлыми ячейками `drawRoundRect` от `#24283B` до `#7DCFFF`).
  3. Столбчатый график активности по месяцам с плавной пружинной анимацией высоты (`animateFloatAsState`).
  4. Кольцевая диаграмма (`drawArc`) топ-5 тегов с легендой.
  5. Горизонтальный таймлайн «Ближайшие открытия» с датами будущих капсул.
- **Обоснование отказа от сторонних библиотек графиков:** Vico / MPAndroidChart добавляют 400–900 КБ к APK и плохо стыкуются с кастомным неоновым свечением Tokyo Night. Чистый Compose `Canvas` занимает ~250 строк кода, поддерживает `semantics` для TalkBack (озвучивание значений каждого столбца/сектора) и 0 КБ лишних зависимостей.
- **Источники данных и производительность на бесплатном хостинге:** Статистика на 100% вычисляется на клиенте из уже загруженного списка `CapsuleDto` (при добавлении поля `createdAt: Long` в `CapsuleDto`). Новых тяжёлых SQL-запросов к Neon Free не требуется (либо 1 легковесный агрегирующий `GET /api/v1/me/stats` при включении пагинации).
- **Новые зависимости:** Нет.
- **Трудозатраты:** **5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Открыть «Моя статистика», проверить корректность счётчиков, анимацию графиков и озвучку значений через TalkBack.

#### №6. QR-код приглашения в капсулу и обмена `@handle` (Категория Г)
- **Описание для пользователя:** На экране профиля и в карточке групповой капсулы можно показать стильный киберпанк-QR-код (для быстрого добавления друга по `@handle` или входа по `inviteCode`), а также поделиться им или считать камерой/по ссылке.
- **Экран (Tokyo Night):** Модальный диалог с матричным QR-кодом, отрисованным бирюзовыми (`#7DCFFF`) и фиолетовыми (`#BB9AF7`) неоновыми модулями на тёмном фоне `#1A1B26`.
- **Сервер и клиент:** Сервер не меняется. На клиенте: генерация QR-матрицы (`com.google.zxing:core:3.5.3`, всего ~120 КБ после R8) и отрисовка через Compose `Canvas`.
- **Новые зависимости:** `com.google.zxing:core:3.5.3`.
- **Трудозатраты:** **2.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Нажать на кнопку QR-кода рядом с `@handle` или кодом групповой капсулы, отсканировать стандартной камерой телефона и проверить переход по диплинку.

#### №7. Биометрия (отпечаток пальца / лицо) для входа в PIN-экран (Категория В)
- **Описание для пользователя:** При возврате в приложение вместо ручного ввода 4 цифр PIN-кода автоматически появляется системное окно сканера отпечатка пальца или распознавания лица (с возможностью в любой момент ввести PIN вручную).
- **Экран (Tokyo Night):** На полноэкранном PIN-экране неоновая иконка биометрии `TokyoCyan` «Разблокировать по биометрии» и переключатель «Вход по отпечатку / лицу» в `SettingsScreen`.
- **Сервер и клиент:** Сервер не меняется. На клиенте: `androidx.biometric:biometric:1.1.0`, смена базового класса `MainActivity` с `ComponentActivity` на `FragmentActivity` (полностью совместима с Compose), вызов `BiometricPrompt` при `isPinLocked == true`.
- **Новые зависимости:** `androidx.biometric:biometric:1.1.0`.
- **Трудозатраты:** **2.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Включить PIN и биометрию в настройках, свернуть и развернуть приложение, разблокировать касанием датчика.

#### №8. Современный Android: Predictive Back, Monochrome-иконка, App Shortcuts (Категория В)
- **Описание для пользователя:**
  1. Плавная системная анимация предпросмотра при свайпе «Назад» на Android 14–15 (включая сворачивание открытых форм с предупреждением о несохранённом черновике через `BackHandler`).
  2. Тематическая монохромная иконка на рабочем столе для Pixel / Android 13+.
  3. Долгое нажатие на иконку приложения открывает быстрые ярлыки: «Запечатать капсулу», «Гео-радар», «Моя полка».
- **Экран (Tokyo Night):** Системный лаунчер и жесты ОС + диалог «Выйти без сохранения черновика?» при свайпе назад с заполненной формы `CreateCapsuleScreen`.
- **Сервер и клиент:** В `AndroidManifest.xml` добавить `android:enableOnBackInvokedCallback="true"` и `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />`; в `ic_launcher.xml` добавить `<monochrome android:drawable="@drawable/ic_launcher_monochrome" />`; в `CreateCapsuleScreen` добавить `BackHandler(enabled = state.hasUnsavedInput)`.
- **Новые зависимости:** Нет.
- **Трудозатраты:** **2.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Зажать иконку приложения на домашнем экране Android, выбрать «Запечатать капсулу» и проверить прямой переход на экран создания.

#### №9. Локальный кеш (Room) и полноценная работа офлайн (Категория В)
- **Описание для пользователя:** Приложение мгновенно показывает полку капсул, открытые тексты и статистику даже в самолёте или в метро без интернета, пока сервер Render Free просыпается в фоне (30–50 секунд). Также можно сохранить черновик капсулы офлайн и отправить позже.
- **Экран (Tokyo Night):** Ненавязчивый тонкий индикатор в верхней панели `TopBar`: «Офлайн-режим • показаны сохранённые данные» (янтарный `#E0AF68`), автоматически сменяющийся на синхронизацию при появлении сети.
- **Сервер и клиент:** Сервер не меняется. На клиенте: база `Room` (`CapsuleEntity`, `StageEntity`, `MediaMetaEntity`, `OfflineDraftEntity`) как единый источник правды (Single Source of Truth): при старте UI мгновенно читает `Flow<List<CapsuleDto>>` из Room, а сетевой ответ обновляет таблицы.
- **Новые зависимости:** `androidx.room:room-runtime:2.6.1`, `androidx.room:room-ktx:2.6.1`, плагин `ksp`.
- **Трудозатраты:** **6 ч** | **Риск:** Средний (важно очищать таблицы Room при выходе из аккаунта и `deleteMyData`).
- **Что сделать вручную:** Ничего.
- **Как проверить:** Загрузить капсулы, включить авиарежим, перезапустить приложение — полка и открытые капсулы отображаются за <100 мс без ошибок.

#### №10. Виджет обратного отсчёта на рабочий стол Android (Jetpack Glance) (Категория В)
- **Описание для пользователя:** Виджет 4×2 или 2×2 на домашнем экране смартфона показывает название ближайшей капсулы, оставшееся количество дней/часов и статус («Созревает» или «Готова к открытию!»), открывая капсулу по клику.
- **Экран (Tokyo Night):** Тёмный виджет `#1A1B26` с неоновым акцентом `#7DCFFF`, прогресс-полоской и кнопкой быстрого создания капсулы `+`.
- **Сервер и клиент:** Сервер не меняется. На клиенте: `GlanceAppWidget` + `GlanceAppWidgetReceiver`, читающий сводку ближайшей капсулы из `DataStore` / Room.
- **Новые зависимости:** `androidx.glance:glance-appwidget:1.1.1`, `androidx.glance:glance-material3:1.1.1`.
- **Трудозатраты:** **4.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Добавить виджет «Time Capsule» на рабочий стол Android и проверить обновление при создании новой капсулы.

#### №11. Диплинки (Custom Scheme + Android App Links через GitHub Pages и `assetlinks.json`) (Категория В)
- **Описание для пользователя:** Ссылка вида `https://<user>.github.io/time-capsule-project/join?code=ABC123` или `timecapsule://join/ABC123` в Telegram/SMS сразу открывает приложение Time Capsule 2.0 с диалогом вступления в групповую капсулу или профилем `@handle` (а если приложение не установлено — веб-страницу со ссылкой на скачивание APK из GitHub Releases).
- **Экран (Tokyo Night):** При переходе по ссылке поверх полки автоматически всплывает предзаполненная карточка подтверждения «Присоединиться к капсуле #ABC123?».
- **Сервер и клиент:** В `AndroidManifest.xml` добавить `intent-filter` с `android:autoVerify="true"` для `https://<owner>.github.io` и схему `timecapsule://`. Подготовить статическую папку `docs/.well-known/assetlinks.json` и мини-лендинг `docs/index.html` в стиле Tokyo Night для GitHub Pages.
- **Новые зависимости:** Нет.
- **Трудозатраты:** **3 ч** | **Риск:** Низкий.
- **Что сделать вручную:** В настройках репозитория GitHub включить **Settings → Pages → Deploy from branch `main` (`/docs`)** и вписать SHA-256 отпечаток своего `release.keystore` в `docs/.well-known/assetlinks.json`.
- **Как проверить:** Отправить ссылку в «Избранное» Telegram, нажать на неё и проверить автопереход в приложение.

#### №12. Серверная надёжность: миграции Flyway, автоочистка устаревших данных и версионирование API (Категория Д)
- **Описание для пользователя:** Обновления сервера никогда не ломают существующую базу в Neon, просроченные сессии и осиротевшие данные автоматически вычищаются, а старые версии APK получают корректный ответ о необходимости обновления.
- **Экран (Tokyo Night):** При несовместимости API клиент показывает штатный экран обязательного обновления (`MandatoryUpdateScreen`).
- **Сервер и клиент:**
  1. Подключить `org.flywaydb:flyway-core` + `flyway-database-postgresql` (`V1__initial_schema.sql`, `V2__indexes_and_reactions.sql`) вместо неконтролируемого `SchemaUtils.createMissingTablesAndColumns`.
  2. Добавить заголовок `X-API-Version: 1` и проверку `X-Client-Version-Code`.
  3. Фоновая задача очистки раз в 6 часов: удаление `Sessions` с `expiresAt < now`.
- **Новые зависимости:** `org.flywaydb:flyway-core:10.17.0`, `flyway-database-postgresql:10.17.0`.
- **Трудозатраты:** **4 ч** | **Риск:** Низкий (`baselineOnMigrate = true` безопасно подхватывает уже созданную в Neon схему).
- **Что сделать вручную:** Ничего.
- **Как проверить:** Запустить `./gradlew test` на бэкенде и проверить успешный накат миграций `V1` и `V2` на чистой и существующей базе.

#### №13. Серверные метрики работоспособности, структурированные логи и защита от злоупотреблений (Категория Д)
- **Описание для пользователя:** Повышение стабильности сервера, защита от спама капсулами/медиа и мгновенная диагностика ошибок.
- **Сервер и клиент:**
  1. Расширенный `/health` и защищённый `/api/v1/admin/metrics` (время аптайма, статус пула БД Hikari, число активных сессий, объём занятого места под медиа в Neon из лимита 512 МБ).
  2. Квоты на пользователя: не более 20 новых капсул в сутки и не более 30 МБ суммарных медиавложений на одного пользователя (защищает лимит 512 МБ диска Neon Free от переполнения одним аккаунтом!).
  3. Подключение `CallLogging` и опционального `Sentry` (`SENTRY_DSN` через env).
- **Новые зависимости:** `io.ktor:ktor-server-call-logging-jvm:2.3.12` (и опционально `io.sentry:sentry:7.14.0`).
- **Трудозатраты:** **3.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** При желании создать бесплатный проект на `sentry.io` и добавить `SENTRY_DSN` в переменные окружения Render (необязательно).
- **Как проверить:** Вызвать `GET /health` и проверить срабатывание квоты `429 Too Many Requests` при превышении суточного лимита создания капсул в тесте.

#### №14. Автоматическое резервное копирование базы Neon Free (GitHub Actions + шифрованный дамп) (Категория Д)
- **Описание для пользователя:** Еженедельная автоматическая резервная копия базы данных PostgreSQL, зашифрованная ключом GPG/AES, чтобы ни одна капсула не пропала даже при сбое облака.
- **Сервер и клиент:** Скрипт `scripts/backup-db.sh` и workflow `.github/workflows/db-backup.yml` (по расписанию `cron: '0 3 * * 0'`), который делает `pg_dump "$DATABASE_URL"`, сжимает `gzip`, шифрует `openssl enc -aes-256-cbc -pbkdf2` и сохраняет как защищённый GitHub Actions Artifact со сроком хранения 30–90 дней.
- **Новые зависимости:** Нет.
- **Трудозатраты:** **2 ч** | **Риск:** Низкий (секреты только в GitHub Actions Secrets, дамп зашифрован).
- **Что сделать вручную:** Добавить `DATABASE_URL` и `BACKUP_PASSPHRASE` в **GitHub Repository Settings → Secrets and variables → Actions**.
- **Как проверить:** Запустить workflow кнопкой `Run workflow` в GitHub Actions и проверить скачивание зашифрованного архива.

#### №15. Поддержка планшетов и складных экранов (`WindowSizeClass`) (Категория В)
- **Описание для пользователя:** На планшетах, раскладушках (Fold) и в альбомной ориентации приложение перестраивается в двухпанельный интерфейс: слева NavigationRail и полка капсул, справа — 3D-капсула или открытый таймлайн этапов.
- **Экран (Tokyo Night):** Вместо нижней `NavigationBar` на экранах `Medium`/`Expanded` отображается боковая неоновая панель `NavigationRail`, а `CapsulesShelfScreen` + `CapsuleStagesTimeline` работают в режиме List-Detail.
- **Сервер и клиент:** Снять `screenOrientation="portrait"` в `AndroidManifest.xml`, подключить `material3-window-size-class` в `MainActivity.kt` и адаптировать сетку карточек (`LazyVerticalGrid` на 2 колонки при `Expanded`).
- **Новые зависимости:** `androidx.compose.material3:material3-window-size-class:1.2.1`.
- **Трудозатраты:** **4.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Повернуть экран или запустить в режиме планшета / Foldable и проверить перестроение `NavigationBar` → `NavigationRail` без потери состояния.

#### №16. Мультиязычность: Русский (RU), Английский (EN) и Таджикский (TG) с выбором языка в настройках (Категория В)
- **Описание для пользователя:** Возможность переключить язык интерфейса между русским, английским и таджикским прямо в настройках приложения без смены системного языка телефона (Android 13+ Per-App Language API + совместимость с Android 8–12).
- **Экран (Tokyo Night):** В `SettingsScreen` селектор «Язык интерфейса: Русский / Тоҷикӣ / English» с мгновенным применением.
- **Сервер и клиент:** Вынести оставшиеся хардкод-строки в `values/strings.xml`, создать полные переводы `values-en/strings.xml` и `values-tg/strings.xml`, добавить `res/xml/locales_config.xml` и вызов `AppCompatDelegate.setApplicationLocales`.
- **Новые зависимости:** `androidx.appcompat:appcompat:1.7.0` (или `LocaleManager` API 33 + ContextWrapper).
- **Трудозатраты:** **4.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Просмотреть ключевые термины в `values-tg/strings.xml` (например, «Капсулаи вақт», «Радар», «Қулф шудааст»).
- **Как проверить:** Переключить язык в `SettingsScreen` на Тоҷикӣ и English и пройти по всем 5 вкладкам.

#### №17. Совместные капсулы с несколькими соавторами («Капсула компании друзей») (Категория Г)
- **Описание для пользователя:** При создании групповой капсулы участники могут в течение 24 часов («окно наполнения») добавить свой собственный секретный этап (текст/фото/голос) в общую капсулу, после чего она окончательно запечатывается до общей даты открытия.
- **Экран (Tokyo Night):** В таймлайне капсулы со статусом «Сбор посланий (осталось 18 ч)» кнопка `TokyoPurple` «Добавить свой вклад в капсулу». Каждый этап в открытой капсуле подписан `@handle` своего автора.
- **Сервер и клиент:** В `CapsuleStages` добавить `authorId = varchar("author_id", 64).nullable()`, эндпоинт `POST /api/v1/capsules/{id}/stages` (доступен участникам, пока `now < sealingDeadline` или до запечатывания).
- **Новые зависимости:** Нет.
- **Трудозатраты:** **5.5 ч** | **Риск:** Средний.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Создать групповую капсулу, войти вторым аккаунтом, добавить свой этап и проверить, что при открытии видны послания обоих участников.

#### №18. Пагинация списков и быстрый поиск по полке капсул (Категория В)
- **Описание для пользователя:** Поиск капсул по названию, тегу или `@handle` отправителя прямо на полке + плавная подгрузка при большом архиве (100+ капсул).
- **Экран (Tokyo Night):** Строка поиска `OutlinedTextField` с иконкой лупы и фильтрацией в реальном времени над вкладками «Все / Отправленные / Входящие», а также курсорная подгрузка `limit` / `beforeTimestamp`.
- **Сервер и клиент:** В `GET /api/v1/capsules` добавить необязательные query-параметры `?limit=50&offset=0` (с сохранением обратной совместимости без параметров). На клиенте: строка поиска на `CapsulesShelfScreen` и дозагрузка при скролле к концу `LazyColumn`.
- **Новые зависимости:** Нет.
- **Трудозатраты:** **3.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Ввести тег или `@handle` в строку поиска на полке и проверить мгновенную фильтрацию списка.

#### №19. Варианты темы внутри палитры Tokyo Night (Classic / Storm / Neon AMOLED + акцент) (Категория В)
- **Описание для пользователя:** Сохраняя фирменный стиль Tokyo Night, пользователь может выбрать в настройках оттенок фона: классический `#1A1B26`, глубокий AMOLED `#0D0E14` (экономия экрана OLED) или мягкий Storm `#24283B`, а также ведущий неоновый акцент (Бирюзовый `#7DCFFF` или Фиолетовый `#BB9AF7`).
- **Экран (Tokyo Night):** В `SettingsScreen` три превью-кружка палитры Tokyo Night с мгновенным переключением.
- **Сервер и клиент:** Расширить `UserPreferences` и `TimeCapsuleTheme` в `Theme.kt` без нарушения констант цветов Tokyo Night.
- **Новые зависимости:** Нет.
- **Трудозатраты:** **2.5 ч** | **Риск:** Низкий.
- **Что сделать вручную:** Ничего.
- **Как проверить:** Переключить режим `Tokyo Night AMOLED` в настройках и проверить отображение карточек и 3D-капсулы.

#### №20. Экспорт открытой капсулы в PDF-письмо и публичная веб-страница по явному согласию автора (Категория Г)
- **Описание для пользователя:**
  1. Кнопка «Сохранить в PDF» формирует стильное многостраничное письмо с текстом всех этапов и фотографией (через встроенный системный `android.graphics.pdf.PdfDocument`, 0 КБ сторонних библиотек).
  2. Опционально (только для открытых капсул и только по явному тумблеру автора с предупреждением о приватности): создание публичной read-only ссылки на открытую капсулу.
- **Экран (Tokyo Night):** В меню открытой капсулы пункты «Экспорт в PDF» и «Публичная ссылка (выкл.)» с диалогом предупреждения, что текст станет доступен по ссылке без входа.
- **Сервер и клиент:** На клиенте `PdfDocument` для генерации PDF. На сервере (если включена публичная ссылка): поле `publicShareToken: varchar(64).nullable().uniqueIndex()` в `Capsules` и read-only `GET /api/v1/public/capsules/{shareToken}` (отдаёт только открытый текст без email и без точных координат дома).
- **Новые зависимости:** Нет.
- **Трудозатраты:** **4.5 ч** (PDF отдельно — 2 ч, публичная ссылка — 2.5 ч) | **Риск:** Средний (требует явного opt-in предупреждения о приватности).
- **Что сделать вручную:** Ничего.
- **Как проверить:** Нажать «Сохранить в PDF» на открытой капсуле и открыть созданный PDF-файл стандартным просмотрщиком.

---

### Блок Д (дополнительно): Что делать при росте нагрузки на бесплатных тарифах (Render Free + Neon Free)
1. **Лимит диска Neon Free (512 МБ):** Главный потребитель места — таблица `capsule_media` (фото до 800 КБ + голос до 1 МБ). Без изменений 512 МБ хватит примерно на ~350–450 медиа-капсул.
   - *Решение в коде:* сжимать фото на клиенте в формат **WebP** (встроено в Android API, даёт 150–250 КБ вместо 800 КБ JPEG при том же качестве) и вводить квоту 30 МБ на пользователя. При приближении БД к 400 МБ — вынести зашифрованные медиа-блобы в бесплатный **Cloudflare R2** (10 ГБ бесплатно без платы за трафик) с сохранением AES-GCM шифрования на сервере.
2. **Лимит Compute Hours Neon Free (191.9 ч/месяц при 0.25 CU):** Чтобы база Neon засыпала вместе с Render при отсутствии пользователей, в `DatabaseConfig.kt` настроить `HikariConfig`: `minimumIdle = 0`, `idleTimeout = 60_000L` (1 минута). Сейчас Hikari держит `maximumPoolSize = 5` и `minimumIdle = 5` по умолчанию, не давая Neon уснуть, пока жив контейнер Render!
3. **Холодный старт Render Free (750 ч/мес, засыпание через 15 мин простоя):** Внедрить локальный кеш `Room` (функция №9), чтобы пользователь вообще не замечал просыпания сервера при открытии приложения.

---

## 5) План реализации по релизам (ШАГ 4)

### Блок «Быстрые победы» (Quick Wins — до 2 часов каждая, можно внедрять точечно)
1. **QW-1 (0.5 ч):** Убрать выгрузку `encryptedBytes` из `buildCapsuleDto` ([CapsuleRoutes.kt:1270](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1270-L1281)) — мгновенная защита от OOM на Render Free.
2. **QW-2 (0.5 ч):** Убрать двойную навигацию `onOpenTimeline` при открытии капсулы с полки ([CapsulesShelfScreen.kt:277](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsulesShelfScreen.kt#L277)).
3. **QW-3 (0.5 ч):** Починить кнопку «Повторить» (`retryTrigger++`) в `PhotoMediaCard` и `VoiceMediaCard` ([CapsuleStagesTimeline.kt:219](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsuleStagesTimeline.kt#L219-L275)) + включить перемотку `Slider` в аудиоплеере.
4. **QW-4 (1.0 ч):** Зафиксировать статус открытой гео-капсулы (`myOpenedAt != null`), чтобы она не блокировалась обратно через 60 секунд ([CapsuleRoutes.kt:642](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L642-L666), [CapsuleRoutes.kt:1239](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L1239-L1268)).
5. **QW-5 (1.5 ч):** Запрос runtime-разрешения `POST_NOTIFICATIONS` + обработка `EXTRA_CAPSULE_ID` в `MainActivity` ([MainActivity.kt:96](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/MainActivity.kt#L96-L124), [CapsuleUnlockWorker.kt:127](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/data/CapsuleUnlockWorker.kt#L127-L145)).
6. **QW-6 (0.5 ч):** Разблокировать шаблоны «Фото-архив момента» и «Голосовое в 2026» в `IdeasScreen.kt` ([IdeasScreen.kt:127](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/IdeasScreen.kt#L127-L148)) и скрыть бейдж `inviteCode` на личных капсулах ([CapsulesShelfScreen.kt:631](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CapsulesShelfScreen.kt#L631-L639)).
7. **QW-7 (0.5 ч):** Удалять запись из `Users` в `DELETE /api/v1/me/data` ([CapsuleRoutes.kt:290](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/CapsuleRoutes.kt#L290-L310)) + логировать ошибки `500` в `StatusPages` ([Application.kt:153](file:///Users/ghost/time-capsule-project/backend/src/main/kotlin/com/timecapsule/server/Application.kt#L153-L157)).
8. **QW-8 (1.5 ч):** Включить создание многоэтапных капсул (кнопки «+ Добавить этап» / удаления этапа) в `CreateCapsuleScreen.kt` ([CreateCapsuleScreen.kt:1082](file:///Users/ghost/time-capsule-project/android-client/app/src/main/java/com/example/cyberpunk/ui/screens/CreateCapsuleScreen.kt#L1082-L1188)).

---

### Релиз R1: Критические исправления, безопасность и производительность БД (1.5–2 дня работы агента)
- **Состав и порядок задач:**
  1. Исправление выгрузки `encryptedBytes` в `buildCapsuleDto` и устранение проблемы `4 + 5×N` запросов (батч-выборка списка капсул).
  2. Исправление повторной блокировки открытых гео-капсул (`myOpenedAt != null`) и разделение `hasUnlockedStage` / `isFullyUnlocked`.
  3. Защита `joinCapsuleByInviteCode` (только для `isGroupCapsule`, без `selectAll()`, двусторонняя проверка ЧС), полное удаление `Users` в `DELETE /api/v1/me/data`.
  4. Защита `AuthRateLimiter` от спуфинга и утечки памяти, жёсткий лимит чтения стрима загрузки медиа (против chunked-обхода), кеширование `GoogleIdTokenVerifier`.
  5. Индексы БД в `Tables.kt`, SQL `LIKE` + `LIMIT` в `searchUsersByQuery`, настройка Hikari `minimumIdle = 0` для экономии часов Neon Free, ротация `CAPSULE_AES_KEY` (`v1:` + `CAPSULE_AES_KEY_PREVIOUS`), фоновая очистка `Sessions`.
  6. Клиентские фиксы: устранение двойной навигации на полке, починка кнопки «Повторить» в медиа-карточках, включение перемотки в аудиоплеере, удаление двойного вызова `authGoogle` в `catch`.
- **Зависимости:** Нет (стартовый релиз).
- **Затронутые модули:** `backend` (`Application.kt`, `CapsuleRoutes.kt`, `Tables.kt`, `DatabaseConfig.kt`, `CryptoUtils.kt`, `Dockerfile`), `android-client` (`CapsulesShelfScreen.kt`, `CapsuleStagesTimeline.kt`, `CapsuleViewModel.kt`, `IdeasScreen.kt`).
- **Критерии готовности (проверяемые):**
  - `GET /api/v1/capsules` на 30 капсулах с медиа выполняется за `<= 5` SQL-запросов и не читает колонку `encrypted_bytes`.
  - Гео-капсула после `POST /open` остаётся разблокированной спустя 61+ секунду.
  - Попытка `POST /capsules/join` для личной (не групповой) капсулы возвращает `403 Forbidden`.
  - `DELETE /api/v1/me/data` удаляет строку из таблицы `users`.
- **Список тестов:** Новые тесты в `BackendUnitTests.kt` (проверка сохранения доступа к гео-капсуле после 60 с, запрет join к личной капсуле, удаление строки `Users`, ротация ключа `CAPSULE_AES_KEY_PREVIOUS`, chunked-лимит медиа) + `./gradlew test lintRelease`.
- **Риски и откат:** Риск регрессии в SQL-батчинге списка капсул; откат — `git revert` коммита релиза R1 (схема БД обратно совместима, так как добавляются только индексы).
- **Ручные шаги пользователя:** До релиза — ничего. После релиза — задеплоить обновлённый бэкенд на Render и выпустить патч-релиз APK через `./release.sh`.

---

### Релиз R2: Уведомления 2.0, безопасность PIN/биометрии и живучесть клиента (1.5–2 дня работы агента)
- **Состав и порядок задач:**
  1. Полный цикл уведомлений (Функция №2): запрос `POST_NOTIFICATIONS` на Android 13+, обработка `EXTRA_CAPSULE_ID` в `MainActivity`, 3 канала уведомлений, точные будильники `AlarmManager` с безопасным fallback на Android 12–15, `BootCompletedReceiver`, «Годовщина капсулы» и «Тихие часы».
  2. Усиление безопасности клиента (Аудит №13 + Функция №7): шифрование `session_token` через Android Keystore, миграция PIN на `PBKDF2` + кулдаун после 5 ошибок, полноэкранный щит блокировки вместо прозрачного диалога, вход по биометрии (`BiometricPrompt`).
  3. Сохранение черновика капсулы и `pendingCameraUri` через `SavedStateHandle`/`rememberSaveable` (Аудит №18), автоочистка временных файлов в `cacheDir` (Аудит №22) и проверка свежести GPS-координат в `LocationProvider` (Аудит №23).
- **Зависимости:** R1.
- **Затронутые модули:** `android-client` (`MainActivity.kt`, `CapsuleUnlockWorker.kt`, `UserPreferences.kt`, `ApiConfig.kt`, `PinLockDialog.kt`, `SettingsScreen.kt`, `CreateCapsuleScreen.kt`, `LocationProvider.kt`, `AndroidManifest.xml`).
- **Критерии готовности (проверяемые):**
  - При включении уведомлений на Android 13+ запрашивается системное разрешение; тап по уведомлению открывает `CapsuleStagesTimeline` нужной капсулы.
  - Перезапуск Activity (`Don't keep activities` / поворот экрана) во время создания капсулы и съёмки камерой сохраняет все поля черновика и фото.
  - После 5 неверных попыток ввода PIN включается 30-секундная блокировка.
- **Список тестов:** Unit-тесты в `UnlockAndConfigUnitTest.kt` на `PBKDF2`-миграцию PIN, кулдаун попыток, расчёт времени «Тихих часов» и расписания годовщин.
- **Риски и откат:** При смене хеша PIN важно сохранить прозрачную проверку старого `SHA-256` с автоматическим перехешированием в `PBKDF2` при первом успешном вводе. Откат — возврат к предыдущему тегу APK.
- **Ручные шаги пользователя:** Проверить биометрию и пуш-уведомление на физическом телефоне после сборки APK.

---

### Релиз R3: Экран «Моя статистика», таймер на Главной, многоэтапные капсулы и Share-карточка (2 дня работы агента)
- **Состав и порядок задач:**
  1. Добавление `createdAt` в `CapsuleDto` и живой обратный отсчёт с прогресс-баром выдержки на `HomeScreen` (Функция №1).
  2. Новый экран «Моя статистика» на Compose `Canvas` с анимацией и поддержкой TalkBack: KPI-карточки, тепловая карта за 6 месяцев, столбчатый график по месяцам, кольцо тегов, линия времени будущих открытий (Функция №5).
  3. Активация UI создания многоэтапных капсул (до 5 этапов с собственными датами и секретными текстами) в `CreateCapsuleScreen` (Улучшение №8).
  4. Генератор неоновой PNG-карточки открытой капсулы и PDF-письма + системный Share Sheet (Функции №3 и №20-PDF).
- **Зависимости:** R1 (требует корректного `hasUnlockedStage` / `isFullyUnlocked` для многоэтапных капсул).
- **Затронутые модули:** `backend` (`ModelsDto.kt`, `CapsuleRoutes.kt`), `android-client` (`ModelsDto.kt`, `HomeScreen.kt`, new `StatsScreen.kt`, `CreateCapsuleScreen.kt`, `CapsuleStagesTimeline.kt`, `MainActivity.kt`).
- **Критерии готовности (проверяемые):**
  - На Главной тикает посекундный таймер до ближайшей капсулы.
  - Экран «Моя статистика» открывается с Главной и из Настроек, корректно рисует 4 графика на `Canvas` даже при 0 или 1 капсуле (пустые состояния без деления на ноль).
  - Пользователь может создать капсулу с 3 этапами на разные даты и экспортировать открытую капсулу в PNG-карточку и PDF.
- **Список тестов:** JVM-тесты агрегатора статистики (`CapsuleStatsCalculatorTest`: подсчёт серий, группировка по месяцам и тегам, граничные случаи с пустым списком и часовыми поясами) + `DtoSerializationTest`.
- **Риски и откат:** Поле `createdAt: Long = 0L` в `CapsuleDto` имеет значение по умолчанию, поэтому старые и новые клиенты полностью совместимы.
- **Ручные шаги пользователя:** Ничего (стандартный деплой сервера и сборка APK).

---

### Релиз R4: Социальные функции («Ответ в будущее», реакции, QR, диплинки, соавторство) (2–3 дня работы агента)
- **Состав и порядок задач:**
  1. Быстрые реакции получателя (`❤️`, `🔥`, `🥹`, `🚀`, `🌌`) и кнопка «Ответить в будущее» в открытой капсуле (Функция №4).
  2. Генерация киберпанк-QR-кода для `@handle` и `inviteCode` групповой капсулы (Функция №6).
  3. Диплинки `timecapsule://` и Android App Links через GitHub Pages (`docs/.well-known/assetlinks.json` + веб-страница приглашения `docs/index.html`) (Функция №11).
  4. Совместные капсулы с несколькими авторами — возможность участникам добавить свой этап в групповую капсулу (Функция №17).
- **Зависимости:** R1, R3.
- **Затронутые модули:** `backend` (`Tables.kt`, `CapsuleRoutes.kt`, `ModelsDto.kt`), `android-client` (`CapsuleStagesTimeline.kt`, `CreateCapsuleScreen.kt`, `SettingsScreen.kt`, `MainActivity.kt`, `AndroidManifest.xml`), `docs/` (для GitHub Pages).
- **Критерии готовности (проверяемые):**
  - Получатель может поставить реакцию на открытую входящую капсулу, и автор видит её на карточке капсулы.
  - Переход по диплинку `timecapsule://join/ABC123` открывает диалог присоединения к капсуле.
  - Второй участник групповой капсулы может добавить собственный этап через `POST /api/v1/capsules/{id}/stages`.
- **Список тестов:** Серверные тесты в `BackendUnitTests.kt` на реакции, права доступа при добавлении этапа соавтором и валидацию лимитов этапов.
- **Риски и откат:** Для работы `https://` App Links без системного диалога выбора браузера требуется точное совпадение SHA-256 сертификата подписи в `assetlinks.json` (схема `timecapsule://` и QR-код работают сразу без настроек домена).
- **Ручные шаги пользователя:**
  1. Вписать SHA-256 отпечаток релизного ключа (выводится скриптом `./release.sh`) в `docs/.well-known/assetlinks.json`.
  2. Включить GitHub Pages для папки `/docs` в настройках репозитория GitHub.

---

### Релиз R5: Офлайн-кеш Room, виджет Glance, пагинация и современный Android 15 (2–3 дня работы агента)
- **Состав и порядок задач:**
  1. Локальная БД `Room` для мгновенного офлайн-отображения полки, открытых капсул, статистики и сохранения черновиков офлайн (Функция №9) + сжатие загружаемых фото в WebP для экономии Neon Free.
  2. Виджет обратного отсчёта на рабочий стол на базе Jetpack Glance (Функция №10).
  3. Поиск и фильтрация на полке + пагинация `GET /api/v1/capsules?limit=&offset=` (Функция №18).
  4. Predictive Back (`enableOnBackInvokedCallback`), монохромная иконка, App Shortcuts (Функция №8) и адаптивная вёрстка `WindowSizeClass` для планшетов/складных экранов (Функция №15).
- **Зависимости:** R2, R3.
- **Затронутые модули:** `android-client` (`data/local/` Room entities & DAO, `widget/` Glance widget, `CapsuleRepository.kt`, `CapsuleViewModel.kt`, `CapsulesShelfScreen.kt`, `MainActivity.kt`, `res/xml/shortcuts.xml`).
- **Критерии готовности (проверяемые):**
  - В авиарежиме приложение запускается и показывает все ранее синхронизированные капсулы и открытые этапы.
  - Виджет Glance добавляется на рабочий стол и отображает отсчёт до ближайшей капсулы.
  - На широком экране (`Medium`/`Expanded`) навигация переключается на `NavigationRail`.
- **Список тестов:** Тесты маппинга `CapsuleDto <-> CapsuleEntity`, тесты поиска и пагинации на сервере и клиенте, `./gradlew assembleRelease test lintRelease`.
- **Риски и откат:** Добавление KSP и Room требует аккуратной настройки правил ProGuard/R8; обязательно прогнать `./gradlew assembleStaging` и проверить `mapping.txt`.
- **Ручные шаги пользователя:** После установки обновления вынести виджет Time Capsule на рабочий стол смартфона.

---

### Релиз R6: Production-эксплуатация (Flyway, квоты, бэкапы), локализация RU/EN/TG и полировка доступности (1.5–2 дня работы агента)
- **Состав и порядок задач:**
  1. Перевод схемы БД на миграции Flyway (`baselineOnMigrate = true`), введение пользовательских квот (20 капсул/сутки, 30 МБ медиа на аккаунт) и метрик `/health` (Функции №12, №13).
  2. GitHub Actions workflow для еженедельного зашифрованного бэкапа базы Neon Free (Функция №14).
  3. Полная локализация интерфейса на Русский (`values`), Английский (`values-en`) и Таджикский (`values-tg`) языки с переключателем в настройках (Функция №16) + темы `Tokyo Night Classic / Storm / AMOLED` (Функция №19).
  4. Комплексная полировка доступности TalkBack (`contentDescription`, `semantics`, touch targets 48 dp, контраст) и декомпозиция `CapsuleViewModel` (Улучшения №14, №15, №16).
- **Зависимости:** R1–R5.
- **Затронутые модули:** `backend` (`DatabaseConfig.kt`, `db/migration/`, `CapsuleRoutes.kt`), `.github/workflows/db-backup.yml`, `android-client` (`values/`, `values-en/`, `values-tg/`, `SettingsScreen.kt`, `Theme.kt`, `CapsuleViewModel.kt`).
- **Критерии готовности (проверяемые):**
  - Flyway успешно применяет миграции на H2 и PostgreSQL; при превышении квоты сервер возвращает `429 Too Many Requests` с понятным текстом на выбранном языке.
  - Переключение языка RU / EN / TG в `SettingsScreen` мгновенно переводит все экраны.
  - Android Lint не выдаёт ни одного предупреждения `ContentDescription` или `MissingTranslation`.
- **Список тестов:** Тесты Flyway-миграций, тесты квот, `./gradlew lintRelease test`.
- **Риски и откат:** Flyway с `baselineOnMigrate = true` безопасен для уже развёрнутой БД Neon; перед раскаткой R6 запустить workflow бэкапа БД.
- **Ручные шаги пользователя:** Прописать `DATABASE_URL` и `BACKUP_PASSPHRASE` в GitHub Secrets для автобэкапов.

---

## 6) Топ-5 рекомендаций и «чего делать не стоит» (ШАГ 5)

### Топ-5 главных рекомендаций (максимальный эффект при минимальных затратах)
1. **Убрать чтение `encryptedBytes` в `buildCapsuleDto` + внедрить батч-загрузку списка капсул и индексы БД (Аудит №1, №8, №9 — 4 ч):** Сразу устраняет угрозу падения контейнера Render Free по `OutOfMemoryError` (512 МБ RAM) и ускоряет открытие полки капсул в 8–10 раз.
2. **Починить повторную блокировку гео-капсул через 60 секунд и закрыть вход по `inviteCode` в личные капсулы (Аудит №2, №6 — 2.5 ч):** Устраняет критическую логическую ошибку, из-за которой открытая гео-капсула снова шифруется, когда пользователь отходит от точки, и закрывает дыру приватности личных капсул.
3. **Довести до конца уведомления на Android 13–15: разрешение `POST_NOTIFICATIONS`, диплинк `EXTRA_CAPSULE_ID` и точные таймеры (Аудит №3 + Функция №2 — 4.5 ч):** Без этого ключевая ценность приложения («узнать, что капсула из прошлого открылась») на современных телефонах Android 13–15 просто не срабатывает.
4. **Запустить живой таймер на Главной, экран «Моя статистика» на Compose Canvas и генерацию PNG-карточки для соцсетей (Функции №1, №3, №5 — 10 ч):** Даёт мощный визуальный «вау-эффект» в стиле Tokyo Night и виральность в соцсетях при нулевой нагрузке на сервер и 0 КБ сторонних библиотек.
5. **Добавить локальный кеш `Room` + сжатие фото в WebP + `Hikari minimumIdle = 0` (Функция №9 + блок Д — 6.5 ч):** Полностью маскирует 40-секундный холодный старт Render Free для пользователя и в 3–4 раза продлевает жизнь бесплатным лимитам Neon Free (512 МБ диска и 191.9 compute-часов в месяц).

---

### Чего делать НЕ стоит (и почему)
1. **Не стоит делать постоянное фоновое отслеживание геопозиции (Background Geofencing / `ACCESS_BACKGROUND_LOCATION` 24/7):**
   - *Причина:* Огромный расход батареи, жёсткие блокировки со стороны политик Android 12–15 и Google Play, а также риск приватности (отслеживание перемещений пользователя в фоне). Чекин по кнопке на экране Гео-радара (`FOREGROUND_ONLY`) честнее, безопаснее и не сажает аккумулятор.
2. **Не стоит запускать на бэкенде внешний пингер каждые 5 минут, чтобы Render Free и Neon Free «никогда не засыпали»:**
   - *Причина:* Бесплатный тариф Neon Free даёт **191.9 compute-часов в месяц**, тогда как в месяце **720 часов**. Если не давать серверу и БД засыпать, лимит Neon Free полностью исчерпается на **8-й день месяца**, и база отключится до конца месяца! Правильное решение — `Room`-кеш на клиенте + `ColdStartRetry` + `minimumIdle = 0` в Hikari.
3. **Не стоит внедрять видео-вложения в капсулы на бесплатном тарифе БД:**
   - *Причина:* Хранение видео в PostgreSQL (`bytea`) исчерпает весь бесплатный лимит Neon Free (512 МБ) всего за 15–25 коротких роликов и вызовет OOM при шифровании в памяти на Render Free. Фото (WebP до 250 КБ) + голосовое послание (M4A до 1 МБ) + текст полностью закрывают эмоциональные сценарии без затрат на CDN.
4. **Не стоит делать сквозное шифрование на пароле пользователя (Client-Side Zero-Knowledge E2EE) без облачного резерва ключей:**
   - *Причина:* Смысл капсулы времени — открыть её через 1–5 лет на новом телефоне. Если ключ шифрования хранится только на старом смартфоне или завязан на забытый через 3 года пароль, капсула будет безвозвратно потеряна. Серверное шифрование `AES-256-GCM` с привязкой к Google-аккаунту — оптимальный баланс безопасности и сохранности памяти.
5. **Не стоит делать публичную ленту всех чужих капсул или карту чужих гео-меток:**
   - *Причина:* Нарушает приватность пользователей (деанонимизация мест жительства/прогулок), требует круглосуточной модерации контента (жалобы, спам, запрещённый контент) и размывает камерную концепцию личных и дружеских капсул времени.

---

## 7) Решения, которые нужны от пользователя

Ниже приведены короткие вопросы, по которым нужно ваше решение перед переходом к реализации выбранных релизов:

1. **Уведомления о входящих капсулах («Вам прислали капсулу»):**
   - *А)* Оставить только полностью автономные локальные уведомления (проверка при входе и фоновым `WorkManager` раз в 6 часов, **без** регистрации проекта в Firebase).
   - *Б)* Подключить Firebase Cloud Messaging (`google-services.json` + ключ в Render) ради мгновенных пушей при получении капсулы и чекине друга.
   - **Рекомендация:** Начать с **А** в R2 (0 внешних ключей и консолей), а **Б** подключить позже по желанию.

2. **Хранение медиафайлов при росте базы (лимит Neon Free 512 МБ):**
   - *А)* Оставить хранение зашифрованных медиа в PostgreSQL (Neon), но включить сжатие фото в WebP (~200 КБ) и квоту 30 МБ на пользователя (хватит на ~1500–2000 капсул с фото).
   - *Б)* Перенести хранение зашифрованных блобов в объектное хранилище Cloudflare R2 (10 ГБ бесплатно, но нужна регистрация R2 API-ключа).
   - **Рекомендация:** **А** (не требует новых облачных аккаунтов и отлично укладывается в лимит при WebP-сжатии).

3. **Домен для ссылок-приглашений (Диплинки):**
   - *А)* Использовать схему `timecapsule://join/ABC123` + бесплатный GitHub Pages вашего репозитория (`https://<owner>.github.io/time-capsule-project/...`).
   - *Б)* Использовать собственный купленный домен.
   - **Рекомендация:** **А** (GitHub Pages бесплатен, уже интегрирован с вашим GitHub-репозиторием и поддерживает `assetlinks.json`).

4. **Поведение уже открытой гео-капсулы после ухода с точки:**
   - *А)* Открытая один раз на локации гео-капсула остаётся навсегда открытой для этого участника (текст, фото и голос можно перечитывать дома).
   - *Б)* Требовать физического присутствия на точке каждый раз, когда пользователь хочет перечитать уже открытую гео-капсулу.
   - **Рекомендация:** **А** (соответствует статусу `OPENED` и ожиданиям пользователя).

5. **Совместные капсулы с несколькими авторами (Функция №17):**
   - *А)* Включать в план R4 (участники групповой капсулы могут докладывать свои этапы/послания до момента запечатывания).
   - *Б)* Ограничиться более простым сценарием «Ответ в будущее» (Функция №4) и реакциями, не усложняя модель этапов соавторством.
   - **Рекомендация:** **Б** для быстрого релиза, либо **А**, если важен сценарий «капсула от всего класса / компании друзей».

6. **Публичная веб-страница открытой капсулы (Функция №20):**
   - *А)* Делать только локальный экспорт в красивую PNG-карточку (Функция №3) и PDF-письмо без публичных ссылок на сервере.
   - *Б)* Добавить также опциональную публичную веб-ссылку `GET /api/v1/public/capsules/{token}` по явному тумблеру автора.
   - **Рекомендация:** **А** (максимальная приватность, нулевая нагрузка на сервер, PNG-карточка идеально подходит для пересылки в мессенджеры).

7. **Приоритет языков локализации (Функция №16):**
   - *А)* Русский + Английский + Таджикский (`RU` / `EN` / `TG`) сразу в одном релизе.
   - *Б)* Сначала только Русский (с выносом всех строк в `strings.xml`), а `EN` и `TG` отложить.
   - **Рекомендация:** **А** (вынос строк всё равно делается один раз, а поддержка `RU`/`EN`/`TG` выгодно выделяет проект).

8. **Мониторинг ошибок сервера:**
   - *А)* Достаточно подробного логирования в стандартный вывод Render (`CallLogging` + стектрейсы в `StatusPages` + расширенный `/health`).
   - *Б)* Подключить внешнюю отправку ошибок в Sentry (`SENTRY_DSN`).
   - **Рекомендация:** **А** (не требует сторонних сервисов и сразу видно в панели Logs на Render).

---

## 8) Ограничения анализа (что нельзя было проверить без устройства или облака)

1. **Физический приёмник GPS/GNSS и геомагнитный компас:** В среде анализа без реального смартфона невозможно измерить физический дрейф координат в плотной застройке, скорость холодного старта GPS-чипа на улице и работу аппаратного датчика ориентации на экране Гео-радара.
2. **Системные окна Google Play Services и датчика биометрии:** Рендеринг системного шторки Credential Manager (выбор Google-аккаунта на устройстве), проверка совпадения SHA-1 сертификата в живой Google Cloud Console и срабатывание физического сканера отпечатка пальца требуют реального Android-устройства.
3. **Энергопотребление и поведение OEM-оболочек (Xiaomi HyperOS / Samsung OneUI) в Doze Mode:** Реальная задержка доставки `WorkManager` / `AlarmManager` при агрессивном энергосбережении вендоров проверяется только на физическом телефоне при выключенном экране.
4. **Живые сетевые тайминги Render Free + Neon Free под нагрузкой:** Точное время холодного старта контейнера Render и пробуждения вычислительного узла Neon проверялось по архитектуре и ретраям (`ColdStartRetry`), но не нагрузочным тестом по живому облачному URL продакшена (во избежание расхода бесплатных лимитов).
5. **Локальный демон Docker Desktop:** На текущей машине демон Docker Desktop не был запущен (`unix:///Users/ghost/.docker/run/docker.sock`), поэтому `Dockerfile` и `docker-compose.yml` проверены статическим анализом, а работа сервера и БД верифицирована через `./gradlew` и H2/PostgreSQL-совместимые тесты.
