package com.example.familibalance.data.repository

import android.content.Context
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.storage.AppSharedState
import com.example.familibalance.data.SecurePrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Интеграционные тесты [FamilyRepositoryImpl] на Robolectric.
 *
 * SyncStatus / AppStateStorage / NetworkMonitor работают по-настоящему
 * (SharedPreferences и ConnectivityManager доступны в Robolectric).
 * SupabaseManager в юнит-тестах не имеет клиента, поэтому push завершается
 * неудачей сразу. Инициирование отправки наблюдаем через
 * [FamilyRepositoryImpl.pushAttemptCount] — dirty-флаг теперь ставится
 * только на время upsert внутри ProfileSync.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilyRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: FamilyRepositoryImpl

    private fun createChild(id: String) =
        ChildProfileState(id = id, name = "Test $id", avatar = "🧒", initialBalance = 100)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AppSharedState.children.clear()
        AppSharedState.selectedChildId = "user_1"
        // Чистим dirty-статусы от предыдущих тестов
        SyncStatus.attach(context)
        SyncStatus.markClean("user_1")
        SyncStatus.markClean("user_2")
    }

    @After
    fun tearDown() {
        AppSharedState.children.clear()
        SyncStatus.markClean("user_1")
        SyncStatus.markClean("user_2")
    }

    @Test
    fun `updateChildProfile saves locally and initiates cloud push`() {
        val child = createChild("user_1")
        AppSharedState.children[child.id] = child
        repository = FamilyRepositoryImpl(context)

        repository.updateChildProfile(child)

        // Локальное сохранение теперь асинхронное (IO): ждём записи в SharedPreferences
        val prefs = SecurePrefs.get(context, "familibalance_prefs")
        val deadline = System.currentTimeMillis() + 3000
        var savedJson = prefs.getString("app_state_json", "") ?: ""
        while (!savedJson.contains("user_1") && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            savedJson = prefs.getString("app_state_json", "") ?: ""
        }
        assertTrue("Локальное состояние должно содержать ребёнка", savedJson.contains("user_1"))

        // Push инициирован: зафиксирована попытка отправки
        assertTrue(repository.pushAttemptCount.get() >= 1)
    }

    @Test
    fun `dirty child is retried on repository init while clean child is not pushed`() {
        val dirtyChild = createChild("user_1")
        val cleanChild = createChild("user_2")
        AppSharedState.children[dirtyChild.id] = dirtyChild
        AppSharedState.children[cleanChild.id] = cleanChild

        // Помечаем user_1 как dirty ДО создания репозитория
        SyncStatus.attach(context)
        SyncStatus.markDirty("user_1")

        repository = FamilyRepositoryImpl(context)

        // retryDirtyChildren в init идёт на Dispatchers.IO — ждём попытку push
        val deadline = System.currentTimeMillis() + 3000
        while (repository.pushAttemptCount.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }

        // Повторная отправка только для user_1 (dirty), user_2 (чистый) не тронут
        assertTrue(repository.pushAttemptCount.get() == 1)
    }

    @Test
    fun `saveAndSync with onlyChildId pushes only that child`() {
        val child1 = createChild("user_1")
        val child2 = createChild("user_2")
        AppSharedState.children[child1.id] = child1
        AppSharedState.children[child2.id] = child2
        repository = FamilyRepositoryImpl(context)

        repository.saveAndSync(onlyChildId = "user_1")

        // Пуш только для user_1: одна попытка, user_2 не затронут
        assertTrue(repository.pushAttemptCount.get() == 1)
    }

    @Test
    fun `getChild returns child from shared state`() {
        val child = createChild("user_1")
        AppSharedState.children[child.id] = child
        repository = FamilyRepositoryImpl(context)

        assertTrue(repository.getChild("user_1") === child)
        assertTrue(repository.getChild("unknown") == null)
    }
}
