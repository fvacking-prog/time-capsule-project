# MORNING_CHECKLIST_2.md — Утренняя инструкция после ночной работы (Фазы R1–R3)

Доброе утро! Все задачи ночного плана (**Фаза R1**, **Фаза R2**, **Фаза R3** и **Финал**) успешно выполнены и протестированы в изолированной ветке `overnight`. Ветка `production` и удалённый репозиторий не изменялись.

Ниже — пошаговый чеклист для проверки, слияния в `production`, деплоя на Render и выпуска новой версии APK.

---

## Шаг 1. Просмотр отчёта и истории коммитов

1. Ознакомьтесь со сводным отчётом в [`OVERNIGHT_REPORT.md`](file:///Users/ghost/time-capsule-project/OVERNIGHT_REPORT.md) и архитектурными решениями в [`DECISIONS.md`](file:///Users/ghost/time-capsule-project/DECISIONS.md).
2. Проверьте список коммитов и тегов, созданных за ночь:
   ```bash
   git status
   git log --oneline production..overnight
   git tag -l
   ```
   Должны присутствовать теги:
   - `before-overnight` — точка до начала ночной работы;
   - `r1-done` — завершение критических исправлений сервера и клиента (Фаза R1);
   - `r2-done` — завершение уведомлений 2.0, защиты PIN/биометрией и стабильности (Фаза R2);
   - `r3-done` — завершение живого таймера, экрана статистики, многоэтапного UI, PNG/PDF-экспорта и доступности (Фаза R3).

3. Если нужно посмотреть полный diff относительно `production`:
   ```bash
   git diff --stat production..overnight
   ```

---

## Шаг 2. Слияние ветки `overnight` в `production`

Когда вы убедились, что изменения вас устраивают, влейте `overnight` в `production`:

```bash
git checkout production
git merge overnight
```

*(Если по какой-то причине захочется откатить только отдельную фазу, можно переключиться на соответствующий тег: `r1-done`, `r2-done` или `r3-done`).*

---

## Шаг 3. Сборка подписанного релизного APK через `release.sh`

Запустите штатный релизный скрипт (он автоматически увеличит `versionCode` и `versionName`, соберёт подписанный `app-release.apk`, проверит подпись через `apksigner` и выведет переменные `LATEST_*` для Render):

```bash
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
./release.sh https://time-capsule-project-fyyy.onrender.com "Уведомления 2.0, биометрия, экран статистики, многоэтапные капсулы и экспорт в PNG/PDF"
```

После завершения `release.sh` закоммитьте обновлённый `versionCode` / `versionName` и `docs/index.html` (если скрипт их обновил):

```bash
git status
git add -A
git commit -m "chore(release): bump version for v2.3 release"
```

---

## Шаг 4. Отправка изменений в GitHub (`git push`)

Отправьте ветку `production` и теги в удалённый репозиторий (это также запустит автодеплой бэкенда на Render и обновит GitHub Pages):

```bash
git push origin production --tags
```

---

## Шаг 5. Публикация APK в GitHub Releases и обновление Render

1. Создайте новый Release на GitHub и прикрепите файл:
   - `android-client/app/build/outputs/apk/release/app-release.apk` (переименуйте или загрузите как `timecapsule-release.apk`, как указано в выводе `release.sh`).
2. В панели **Render Dashboard → Environment** обновите переменные окружения из вывода `release.sh`:
   - `LATEST_VERSION_CODE`
   - `LATEST_VERSION_NAME`
   - `LATEST_APK_URL`
   - `LATEST_APK_SHA256`
   - `LATEST_CHANGELOG`
   - *(Опционально, при будущей ротации ключа шифрования)*: старый ключ можно перенести в `CAPSULE_AES_KEY_PREVIOUS`, а в `CAPSULE_AES_KEY` задать новый 32-байтный Base64-ключ.

---

## Шаг 6. Настройка Health Check и Uptime-монитора на `/ping`

Для экономии лимита 100 CU-часов в месяц на **Neon Free PostgreSQL**:
1. В **Render Dashboard → Settings → Health Check Path** укажите `/ping` (или оставьте `/health`, если хотите проверять БД при деплое, но для внешнего пингера обязательно используйте `/ping`).
2. Во внешнем Uptime-мониторе (UptimeRobot / Cron-job.org), который будит Render каждые 10–14 минут, поменяйте URL с `/health` на:
   ```text
   https://time-capsule-project-fyyy.onrender.com/ping
   ```
   Эндпоинт `GET /ping` возвращает `200 ok` мгновенно без обращения к PostgreSQL, благодаря чему база Neon спокойно засыпает при отсутствии реальных пользователей (`minimumIdle = 0`, `idleTimeout = 60000`).
