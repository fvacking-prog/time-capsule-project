# MORNING_CHECKLIST.md

Простая пошаговая инструкция запуска **Time Capsule 2.0** в продакшн. Почти все действия делаются кликами в браузере и GitHub Desktop (единственный запуск в терминале — кнопка сборки APK через `./release.sh` на шаге 5).

> **Важно:** файлы секретов (`release.keystore`, `keystore.properties`, `android-client/local.properties`, `.env`) защищены `.gitignore` — на GitHub они не попадут. Не загружайте их вручную!

---

## Шаг 1. Загрузить код на GitHub (через GitHub Desktop)

1. Откройте [github.com](https://github.com) в браузере и войдите в свой аккаунт.
2. Вверху справа нажмите **`+`** → **`New repository`**.
3. Заполните:
   - **`Repository name`**: `time-capsule-project` (или любое удобное имя).
   - Выберите **`Public`** *(на бесплатном тарифе GitHub Pages работает только для `Public`-репозиториев)*.
   - **Не ставьте** галочки на `Add a README file` и `.gitignore`.
   - Нажмите зелёную кнопку **`Create repository`**.
   - На открывшейся странице скопируйте HTTPS-ссылку вида `https://github.com/ВАШ_ЛОГИН/time-capsule-project.git`.
4. Откройте приложение **GitHub Desktop** на Mac:
   - Нажмите **`File`** → **`Add Local Repository...`** → **`Choose...`** → выберите папку `time-capsule-project` → **`Add Repository`**.
   - Вверху в поле **`Current branch`** убедитесь, что выбрана ветка **`production`**.
   - Если кнопка справа сверху называется **`Publish repository`**: нажмите **`Publish repository`**.
   - Если же кнопка показывает **`Publish branch`** (или привязка к удалённому репозиторию не задана): откройте меню **`Repository`** → **`Repository Settings...`** → **`Remote`** → вставьте URL из пункта 3 → нажмите **`Save`**, после чего нажмите синюю кнопку **`Publish branch`** вверху справа.
5. Проверьте на GitHub в браузере, что появилась ветка **`production`** и в ней есть папка `backend/` и папка `docs/`.

---

## Шаг 2. Создать постоянную базу PostgreSQL в Neon

1. Откройте [console.neon.tech](https://console.neon.tech) и войдите через GitHub или Google.
2. Нажмите кнопку **`New Project`** (или **`Create a project`**).
3. Введите название **`timecapsule`**, выберите ближайший регион (например `AWS / Europe (Frankfurt)` или `US East`), оставьте версию PostgreSQL по умолчанию и нажмите **`Create project`**.
4. На экране проекта нажмите зелёную кнопку **`Connect`** (справа вверху).
5. В открывшемся окне:
   - Включите переключатель **`Show password`** (чтобы пароль показался вместо звёздочек, если скрыт) — или нажмите **`Copy snippet`** возле строки **`Connection string`**.
   - Убедитесь, что строка имеет вид:
     `postgresql://neondb_owner:ПАРОЛЬ@ep-....aws.neon.tech/neondb?sslmode=require`
6. Сохраните эту строку (она понадобится как `DATABASE_URL` на шаге 3).

---

## Шаг 3. Развернуть постоянный сервер на Render (Docker из папки `backend`)

1. Откройте [dashboard.render.com](https://dashboard.render.com) и войдите (удобнее через GitHub).
2. Нажмите **`New +`** → **`Web Service`**.
3. В списке репозиториев найдите ваш `time-capsule-project` и нажмите кнопку **`Connect`**.
4. Заполните настройки сервиса **строго так**:
   - **`Name`**: `timecapsule-backend`
   - **`Branch`**: выберите **`production`**
   - **`Root Directory`**: введите **`backend`** *(обязательно! Иначе Render не найдёт `backend/Dockerfile`)*
   - **`Language` / `Runtime`**: выберите **`Docker`**
   - **`Instance Type`**: выберите **`Free`** *(если сервер «уснул», приложение само повторит запрос и покажет баннер «Сервер просыпается, подождите до минуты»)*
5. Прокрутите ниже до блока **`Environment Variables`**. Нажмите **`Add Environment Variable`** 3 раза и заполните **точные имена переменных**:
   - **`DATABASE_URL`** = строка подключения из Neon (Шаг 2: `postgresql://...`).
   - **`CAPSULE_AES_KEY`** = 32-байтный ключ шифрования в Base64 (44 символа, оканчивается на `=`).
     - *Где взять:* скопируйте значение `CAPSULE_AES_KEY` из вашего локального файла `.env` (если создавали его) **или** один раз выполните `./generate-aes-key.sh` и вставьте строку. *(Важно: стандартную кнопку `Generate` внутри Render для этой переменной не используйте — она создаёт строку другого формата, и сервер не запустится!)*
   - **`GOOGLE_WEB_CLIENT_ID`** = ваш Web Client ID из Google Cloud вида `1234567890-abcdef.apps.googleusercontent.com` *(смотрите Шаг 4 или откройте `android-client/local.properties` в TextEdit)*.
6. Нажмите **`Deploy Web Service`**.
7. Подождите 3–5 минут, пока статус сменится на **`Live`**.
8. Вверху страницы Render скопируйте адрес вашего сервера (например `https://timecapsule-backend.onrender.com`). Откройте в браузере `https://ВАШ_АДРЕС.onrender.com/health` — должно открыться короткое слово **`ok`**.

---

## Шаг 4. Настроить Google Cloud Console (`Production` + SHA-1 релизного и отладочного ключей)

1. Откройте [console.cloud.google.com](https://console.cloud.google.com) и выберите ваш проект.
2. В левом меню выберите **`APIs & Services`** → **`OAuth consent screen`** *(или раздел **`Google Auth platform`** → **`Audience`**)*.
3. В разделе **`Publishing status`** нажмите кнопку **`Publish App`** → **`Confirm`**, чтобы перевести приложение из статуса *Testing* в статус **`In production`**. После этого войти сможет любой пользователь со своим Google-аккаунтом.
4. Перейдите в левом меню в **`APIs & Services`** → **`Credentials`** *(или **`Clients`**)*.
5. Проверьте, что в списке есть клиент типа **`Web application`** — его **`Client ID`** (оканчивается на `.apps.googleusercontent.com`) должен совпадать с `GOOGLE_WEB_CLIENT_ID` на Render и в `android-client/local.properties`.
6. Добавьте **Android Client ID для релизной подписи** (`release.keystore`), если он ещё не добавлен:
   - Нажмите **`+ CREATE CREDENTIALS`** → **`OAuth client ID`**.
   - **`Application type`**: выберите **`Android`**.
   - **`Name`**: `Time Capsule Release`.
   - **`Package name`**: **`com.example.cyberpunk`** *(не меняйте!)*.
   - **`SHA-1 certificate fingerprint`**: вставьте **`A3:D1:F2:0F:A9:E3:DD:9D:DD:5A:5F:8A:5E:C6:A0:26:D6:84:9A:46`**.
   - Нажмите **`Create`**.
7. Добавьте **Android Client ID для отладочной сборки** (для тестов из Android Studio / debug APK), если его ещё нет:
   - Нажмите **`+ CREATE CREDENTIALS`** → **`OAuth client ID`**.
   - **`Application type`**: выберите **`Android`**.
   - **`Name`**: `Time Capsule Debug`.
   - **`Package name`**: **`com.example.cyberpunk`**.
   - **`SHA-1 certificate fingerprint`**: вставьте **`37:CA:0F:A2:49:70:75:95:26:B8:65:6A:41:E8:AA:51:3C:38:C6:C0`**.
   - Нажмите **`Create`**.

---

## Шаг 5. Собрать релизный APK (`release.sh`), выложить в GitHub Releases и включить автообновление

> **Почему нужно запускать `./release.sh` после шагов 1–4:** релизный APK привязывается при сборке к вашему реальному HTTPS-адресу Render (`https://...`) и подписывается вашим `release.keystore`.

1. Убедитесь, что в файле `android-client/local.properties` на вашем компьютере заполнена строка `GOOGLE_WEB_CLIENT_ID=...` (Web Client ID).
2. Запустите скрипт релиза (подставьте ваш HTTPS-адрес Render и `ВАШ_ЛОГИН/time-capsule-project`):
   ```bash
   ./release.sh https://ВАШ_АДРЕС.onrender.com ВАШ_ЛОГИН/time-capsule-project
   ```
3. Дождитесь завершения. Скрипт сам:
   - поднимет `versionCode` на +1;
   - соберёт подписанный релизный APK;
   - проверит подпись через `apksigner verify`;
   - сохранит файл **`android-client/app/build/outputs/apk/release/timecapsule-release.apk`**;
   - выведет готовый блок переменных `LATEST_*`. **Не закрывайте это окно — скопируйте эти 5 строк!**
4. Опубликуйте APK на GitHub:
   - Откройте ваш репозиторий на GitHub в браузере → справа в блоке **`Releases`** нажмите **`Create a new release`** *(или **`Draft a new release`**)*.
   - Нажмите **`Choose a tag`** → введите тег из вывода скрипта (например `v1.0.0-c2`) → выберите **`Create new tag: ... on publish`**.
   - Убедитесь, что **`Target`** справа от тега — ветка **`production`**.
   - Введите **`Release title`**: например `Time Capsule 2.0 (v1.0.0)`.
   - Внизу в поле **`Attach binaries by dropping them here or selecting them`** перетащите файл **`timecapsule-release.apk`** из папки `android-client/app/build/outputs/apk/release/`.
   - Нажмите зелёную кнопку **`Publish release`**.
5. Передайте данные релиза серверу на Render (чтобы работала кнопка «Проверить обновления» и проверка при старте):
   - Откройте сервис на **Render** → вкладка **`Environment`** → нажмите **`Edit`**.
   - Через **`Add Environment Variable`** добавьте точные имена и значения из шага 3:
     - **`LATEST_VERSION_CODE`** = *(значение из вывода `release.sh`, например `2`)*
     - **`LATEST_VERSION_NAME`** = *(например `1.0.0`)*
     - **`LATEST_APK_URL`** = *(например `https://github.com/ВАШ_ЛОГИН/time-capsule-project/releases/download/v1.0.0-c2/timecapsule-release.apk`)*
     - **`LATEST_APK_SHA256`** = *(64 символа SHA-256 из вывода `release.sh`)*
     - **`LATEST_MIN_SUPPORTED_VERSION_CODE`** = `1` *(при поднятии выше текущей версии приложение покажет обязательный диалог обновления)*
     - **`LATEST_CHANGELOG`** = *(необязательно, например: `Первый релиз Time Capsule 2.0`)*
   - Нажмите **`Save, rebuild, and deploy`** *(или **`Save Changes`** — Render перезапустит сервер)*.
6. Откройте **GitHub Desktop** → увидите изменение `versionCode` в `android-client/app/build.gradle.kts` → введите описание (например `Release v1.0.0-c2`), нажмите **`Commit to production`** и затем **`Push origin`**.

---

## Шаг 6. Включить страницу загрузки на GitHub Pages (из папки `docs`)

1. В браузере откройте ваш репозиторий на **GitHub** и перейдите на вкладку **`Settings`** (вверху справа).
2. В левом меню выберите пункт **`Pages`** (в разделе *Code and automation*).
3. В блоке **`Build and deployment`**:
   - **`Source`**: оставьте **`Deploy from a branch`**.
   - **`Branch`**: в первом списке выберите **`production`**, во втором списке рядом (где написано `/ (root)`) выберите **`/docs`**, затем нажмите **`Save`**.
4. Через 1–2 минуты обновите эту же страницу — сверху появится зелёная/синяя плашка с кнопкой **`Visit site`** и адресом вида:
   `https://ВАШ_ЛОГИН.github.io/time-capsule-project/`
5. Откройте эту ссылку в браузере на своём Android-телефоне:
   - Нажмите большую кнопку **`Скачать для Android`** — браузер сам скачает `timecapsule-release.apk` из вашего последнего GitHub Release.
   - Откройте скачанный `.apk` файл.
   - Когда Android попросит разрешение, нажмите **`Настройки`** и **разрешите установку из этого источника** для вашего браузера.
   - Вернитесь назад, нажмите **`Установить`**, откройте **Time Capsule 2.0** и войдите через Google!
