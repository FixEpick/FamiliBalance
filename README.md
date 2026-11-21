# FamiliBalance

Приложение для геймификации семейных задач и управления балансом ребёнка. Родители назначают квесты, ребёнок выполняет их и получает награды на «космо-счёт», а прогресс и достижения отслеживаются в реальном времени.

## Основные возможности (Features)

- **Система квестов** — ежедневные, встроенные и пользовательские задания с разными типами подтверждения (фото, комментарий).
- **Управление балансом и наградами** — виртуальная валюта, бонусы за стрик, цель-мечта (копилка), вывод средств через заявку на подтверждение родителем.
- **Отслеживание прогресса и достижений** — система ачивок с уровнями, история операций, панель прогресса для родителя.
- **Честное признание (Honest Cat)** — ребёнок может сам признаться в невыполненном задании на льготных условиях.
- **Родительский контроль** — PIN-код родителя, панель верификации заявок, управление детьми и настройками.

## Безопасность (Security)

- **Row Level Security (RLS)** в Supabase — данные каждого родителя изолированы на уровне базы данных.
- **Шифрование локальных данных** через `EncryptedSharedPreferences`.
- **Защита от скриншотов** — `FLAG_SECURE` на экранах с чувствительной информацией.
- **Проверка цифровой подписи** при обновлении приложения (SEC-03).

## Технологии (Tech Stack)

- **Kotlin** + **Jetpack Compose** (Material 3)
- **MVI архитектура** (ViewModel + StateFlow, однонаправленный поток данных)
- **Supabase** — PostgreSQL, Realtime, Auth
- **Ktor** — сетевой слой
- **Coil** — загрузка изображений
- **Navigation Compose**, **DataStore**, **EncryptedSharedPreferences**

## Локализация

Поддержка **русского (RU)** и **английского (EN)** языков с переключением в приложении.

## Как собрать (How to build)

Требуется **JDK 17**.

```bash
./gradlew assembleDebug
```

> **Важно (SEC-03):** для сборки **release**-версии в корне проекта должен лежать файл `key.properties` с параметрами подписи (см. пример в `key.properties.example`). Без него сборка `assembleRelease` упадёт с ошибкой — это намеренная защита от случайной публикации неподписанного APK.

```properties
# key.properties
storeFile=...
storePassword=...
keyAlias=...
keyPassword=...
```

## Baseline Profile

AOT-профиль для ускорения запуска и снижения jank в child/admin секциях. Исходник профиля коммитится в репозиторий: `app/src/main/generated/baselineProfiles/baseline-prof.txt`.

**Генерация (основной способ):**

```bash
./gradlew :app:generateBaselineProfile
```

Задача собирает `nonMinifiedRelease`-варианты app и тестового модуля `baselineprofile`, прогоняет `BaselineProfileGenerator` на подключённом устройстве/эмуляторе и кладёт профиль в `app/src/main/generated/baselineProfiles/`.

**Ручной прогон (fallback):** на некоторых устройствах (напр. TECNO KJ5n, API 33) gradle-задача периодически зависает в D-state. Тогда:

```bash
# 1. Собрать APK'и
./gradlew :app:assembleNonMinifiedRelease :baselineprofile:assembleNonMinifiedRelease

# 2. Установить на устройство (или эмулятор — профиль от устройства не зависит)
adb install -r app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk
adb install -r baselineprofile/build/outputs/apk/nonMinifiedRelease/baselineprofile-nonMinifiedRelease.apk

# 3. Прогнать тест
adb shell am instrument -w -r \
  -e class com.example.familibalance.baselineprofile.BaselineProfileGenerator#startup \
  com.example.familibalance.baselineprofile/androidx.test.runner.AndroidJUnitRunner

# 4. Забрать профиль
adb pull /storage/emulated/0/Android/media/com.example.familibalance.baselineprofile/BaselineProfileGenerator_startup-baseline-prof.txt \
  app/src/main/generated/baselineProfiles/baseline-prof.txt
```

> **Совет:** если физическое устройство зависает в D-state — запустите те же команды на эмуляторе (`adb -s emulator-5554 ...`). Профиль (HRF-правила по именам классов) от устройства не зависит.

**Как это работает:** генератор запускает приложение с Intent extra `SKIP_ONBOARDING_FOR_TEST=admin` — `MainActivity`/`MainViewModel` в этом режиме пропускают онбординг и открывают `AdminRoute` с тестовыми детьми (`util/BenchmarkSession.kt`). Сценарий проходит 4 вкладки админки (Проверка/Прогресс/История/Настройки), затем «Войти как ребёнок» и 4 вкладки детского раздела. В обычной жизни флаг не выставляется, логика не меняется.

**Когда перегенерировать:** после значимых изменений UI/навигации в child/admin секциях.

## CI/CD

В проекте настроен **GitHub Actions** (`.github/workflows/android_ci.yml`): при каждом пуше и pull request в `master`/`main` автоматически прогоняются unit-тесты (`testDebugUnitTest`) и собирается debug-версия (`assembleDebug`). Release-сборка в CI не выполняется из-за защиты SEC-03.
