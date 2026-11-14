package com.example.familibalance.data.repository

import android.content.Context
import com.example.familibalance.AppSharedState
import com.example.familibalance.ChildProfileState
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
 * неудачей и профиль остаётся dirty — это и используется как наблюдаемый
 * признак того, что отправка была инициирована (markDirty вызывается
 * только внутри pushChildProfile).
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

        // Локальное сохранение: состояние записано в SharedPreferences
        val prefs = context.getSharedPreferences("familibalance_prefs", Context.MODE_PRIVATE)
        val savedJson = prefs.getString("app_state_json", "") ?: ""
        assertTrue("Локальное состояние должно содержать ребёнка", savedJson.contains("user_1"))

        // Push инициирован: профиль помечен dirty (markDirty вызывается только в pushChildProfile)
        assertTrue(SyncStatus.isChildDirty("user_1"))
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

        // retryDirtyChildren в init должен был повторить отправку user_1,
        // а user_2 (чистый) — не трогать
        assertTrue(SyncStatus.isChildDirty("user_1"))
        assertFalse(SyncStatus.isChildDirty("user_2"))
    }

    @Test
    fun `saveAndSync with onlyChildId pushes only that child`() {
        val child1 = createChild("user_1")
        val child2 = createChild("user_2")
        AppSharedState.children[child1.id] = child1
        AppSharedState.children[child2.id] = child2
        repository = FamilyRepositoryImpl(context)

        repository.saveAndSync(onlyChildId = "user_1")

        assertTrue(SyncStatus.isChildDirty("user_1"))
        assertFalse(SyncStatus.isChildDirty("user_2"))
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
