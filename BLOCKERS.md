# BLOCKERS.md

Список шагов или проверок, требующих окружения/действий на стороне человека.

## 1. Docker / Docker Compose (Фаза 4)
- **Что не выполнено**: Сборка Docker-образа из `backend/Dockerfile` (`docker build`), запуск `docker compose -f docker-compose.dev.yml up` и интеграционный прогон на живом контейнере PostgreSQL.
- **Почему**: Утилита `docker` установлена (`/usr/local/bin/docker`, v29.9.2), но Docker Desktop демон на этой машине не запущен (`Cannot connect to the Docker daemon at unix:///Users/ghost/.docker/run/docker.sock`).
- **Что сделать человеку**:
  1. Запустить приложение Docker Desktop на Mac.
  2. Проверить локально или сразу развернуть на Render (см. `MORNING_CHECKLIST.md`) и при желании выполнить:
     ```bash
     export CAPSULE_AES_KEY=$(./generate-aes-key.sh)
     docker compose -f docker-compose.dev.yml up --build
     ```

## 2. Физическое устройство / Android-эмулятор
- **Что не выполнено**: Живое прохождение на экране телефона: реальный Google Credential Manager (требует Google Play Services и SHA-1 в Google Cloud), GPS-чекин на местности, установка APK поверх текущей версии через системный установщик (`FileProvider` + `REQUEST_INSTALL_PACKAGES`).
- **Почему**: Автономная сборка без подключённого устройства/запущенного эмулятора и до настройки Google Cloud / Render / GitHub Releases.
- **Что сделать человеку**: Пройти простые шаги из `MORNING_CHECKLIST.md` и установить собранный релизный APK на Android-устройство.
