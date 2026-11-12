# Time Capsule Backend (Ktor 3.0.1 + Exposed 0.56.0 + PostgreSQL / H2)
1. Требования: JDK 17 (или JRE 21 в `backend/Dockerfile`), сервер слушает `0.0.0.0:${PORT:-8080}`.
2. Переменная окружения `CAPSULE_AES_KEY` обязательна всегда (32 байта AES-256-GCM в формате Base64); без неё сервер завершает старт с ошибкой.
3. Подключение к БД: при заданной `DATABASE_URL` (например от Neon `postgresql://user:pass@host/db?sslmode=require`) используется PostgreSQL + `HikariCP` (`maxPoolSize=3`, `connectionTimeout=10 s`), иначе — файловая H2.
4. Сгенерируйте ключ из корня репозитория: `export CAPSULE_AES_KEY=$(../generate-aes-key.sh)` (или скопируйте `../.env.example` в `../.env`).
5. Сборка и тесты: `cd /Users/ghost/time-capsule-project/backend && ./gradlew test build fatJar --no-daemon`
6. Запуск сервера: `cd /Users/ghost/time-capsule-project/backend && CAPSULE_AES_KEY=... ./gradlew run`
7. Проверка: `curl http://localhost:8080/health` и `curl http://localhost:8080/api/v1/app/version`
