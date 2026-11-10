# Time Capsule Backend (Ktor 3.0.1 + Exposed 0.56.0 + H2)
1. Требования: JDK 17 (Temurin 17), сервер слушает `0.0.0.0:8080`.
2. По умолчанию используется встроенный демо-ключ AES-256-GCM (32 байта в Base64).
3. Чтобы задать свой ключ шифрования через переменную окружения `CAPSULE_AES_KEY`:
4. Сгенерируйте случайные 32 байта в Base64: `export CAPSULE_AES_KEY=$(openssl rand -base64 32)`
5. Или закодируйте ровно 32 ASCII-символа: `export CAPSULE_AES_KEY=$(printf '0123456789abcdef0123456789abcdef' | base64)`
6. Для включения демо-эндпоинта `/demo-force-checkin`: `export DEMO_MODE=true`
7. Сборка и тесты: `cd /Users/ghost/time-capsule-project/backend && ./gradlew test build`
8. Запуск сервера: `cd /Users/ghost/time-capsule-project/backend && DEMO_MODE=true ./gradlew run`
9. Проверка: `curl http://localhost:8080/health` и `curl http://localhost:8080/api/v1/capsules`
