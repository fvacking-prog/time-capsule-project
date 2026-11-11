# Time Capsule Backend (Ktor 3.0.1 + Exposed 0.56.0 + H2)
1. Требования: JDK 17 (Temurin 17), сервер слушает `0.0.0.0:8080`.
2. Переменная окружения `CAPSULE_AES_KEY` обязательна всегда (32 байта AES-256-GCM в формате Base64); без неё сервер завершает старт с ошибкой.
3. Сгенерируйте ключ из корня репозитория: `export CAPSULE_AES_KEY=$(../generate-aes-key.sh)` (или `openssl rand -base64 32`).
4. Опционально скопируйте `../.env.example` в `../.env` и заполните `CAPSULE_AES_KEY` и `GOOGLE_WEB_CLIENT_ID`.
5. Сборка и тесты: `cd /Users/ghost/time-capsule-project/backend && ./gradlew test build fatJar`
6. Запуск сервера: `cd /Users/ghost/time-capsule-project/backend && CAPSULE_AES_KEY=... ./gradlew run`
7. Проверка: `curl http://localhost:8080/health`
