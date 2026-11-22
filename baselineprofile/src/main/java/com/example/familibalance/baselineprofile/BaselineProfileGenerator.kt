package com.example.familibalance.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Генератор Baseline Profile: прогоняет типичный пользовательский путь
 * (запуск, скролл главного экрана, переключение всех четырёх вкладок
 * нижней навигации) и записывает профиль AOT-компиляции в
 * app/src/main/generated/baselineProfiles/.
 *
 * ВАЖНО: сценарий использует ТОЛЬКО raw input (device.click/swipe) и
 * Thread.sleep — без UiAutomator-селекторов (findObject/hasObject).
 * На целевом устройстве (TECNO KJ5n) запросы accessibility-дерева
 * зависают навсегда, а инъекция ввода работает стабильно.
 *
 * В детском и родительском кабинетах ровно 4 вкладки нижней навигации
 * с одинаковыми позициями, поэтому последовательный клик по 4 позициям
 * покрывает оба раздела независимо от того, какой показан.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = "com.example.familibalance",
        // Одна итерация: на TECNO KJ5n (API 33) повторные прогоны
        // collect нестабильно зависают в D-state (I/O), а одиночный
        // прогон детерминирован и занимает ~40 сек. Профиль валиден.
        maxIterations = 1,
        stableIterations = 1,
        profileBlock = {
            // Приложение в режиме бенчмарка пропускает онбординг/вход
            // и открывает AdminRoute с тестовыми детьми
            // (BenchmarkSession в MainActivity/MainViewModel).
            startActivityAndWait {
                it.putExtra(
                    "SKIP_ONBOARDING_FOR_TEST",
                    "admin"
                )
            }
            // Splash 2.4с + settle сессии ~1.5с + init ViewModel
            // (ensureSession + создание тестовых детей).
            Thread.sleep(7000)

            val w = device.displayWidth
            val h = device.displayHeight

            // Центр нижней навигации: NavigationBar ~80dp над системным
            // inset'ом — точка h-80dp попадает внутрь панели и при
            // gesture-, и при 3-кнопочной навигации.
            val density = InstrumentationRegistry.getInstrumentation()
                .targetContext.resources.displayMetrics.density
            val navY = h - (80 * density).toInt()

            fun scrollList() {
                device.swipe(w / 2, (h * 0.75).toInt(), w / 2, (h * 0.4).toInt(), 20)
                Thread.sleep(700)
                device.swipe(w / 2, (h * 0.4).toInt(), w / 2, (h * 0.75).toInt(), 20)
                Thread.sleep(700)
            }

            // 1. Админка: 4 вкладки (Проверка/Прогресс/История/Настройки).
            for (i in 0..3) {
                device.click(w * (2 * i + 1) / 8, navY)
                Thread.sleep(1500)
                scrollList()
            }

            // 2. «Войти как ребёнок» — OutlinedButton под переключателем
            //    детей в AdminTopBar (~18% высоты экрана).
            device.click(w / 2, (h * 0.18).toInt())
            Thread.sleep(2000)

            // 3. Детский раздел (предпросмотр родителя): 4 вкладки
            //    (Главная/Задания/Достижения/История).
            for (i in 0..3) {
                device.click(w * (2 * i + 1) / 8, navY)
                Thread.sleep(1500)
                scrollList()
            }
        }
    )
}
