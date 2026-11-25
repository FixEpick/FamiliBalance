package com.example.familibalance

import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.model.QuestStatus
import com.example.familibalance.data.model.toDto
import com.example.familibalance.data.storage.AppSharedState
import com.example.familibalance.data.storage.AppStateStorage
import com.example.familibalance.data.supabase.ProfileSync
import com.example.familibalance.data.repository.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Регрессионные тесты гонки state_json (approve родителя vs stale push ребёнка).
 *
 * Сценарий: родитель одобряет квест в облаке (stateVersion+1), у ребёнка
 * локальный кэш ещё хранит PENDING. Раньше фоновый sync/push перезаписывал
 * облако устаревшим снапшотом и статус откатывался. Теперь last-writer-wins
 * идёт по монотонному stateVersion, а не по клиентским часам.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncConflictTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun child(id: String = "user_1") = ChildProfileState(
        id = id,
        name = "Test",
        avatar = "🧒",
        initialBalance = 100
    )

    @Before
    fun setUp() {
        SyncStatus.attach(context)
        AppSharedState.children.clear()
    }

    @After
    fun tearDown() {
        AppSharedState.children.clear()
        SyncStatus.markClean("user_1")
        SyncStatus.markClean("user_2")
    }

    // ---------- LWW-решение (чистая функция) ----------

    @Test
    fun `remote with higher stateVersion wins regardless of clocks`() {
        // Часы ребёнка «убегают вперёд»: ts локали больше, но версия ниже.
        assertTrue(
            ProfileSync.isRemoteStateNewer(
                remoteVersion = 6, remoteTs = 1_000,
                localVersion = 5, localTs = 9_999_999
            )
        )
    }

    @Test
    fun `local with higher stateVersion wins regardless of clocks`() {
        assertFalse(
            ProfileSync.isRemoteStateNewer(
                remoteVersion = 5, remoteTs = 9_999_999,
                localVersion = 6, localTs = 1_000
            )
        )
    }

    @Test
    fun `legacy profiles without version fall back to timestamp`() {
        // Оба stateVersion = 0 (старые профили до введения счётчика).
        assertTrue(
            ProfileSync.isRemoteStateNewer(
                remoteVersion = 0, remoteTs = 2_000,
                localVersion = 0, localTs = 1_000
            )
        )
        assertFalse(
            ProfileSync.isRemoteStateNewer(
                remoteVersion = 0, remoteTs = 1_000,
                localVersion = 0, localTs = 2_000
            )
        )
        // remoteTs == 0 — метки нет, удалённая не считается новее.
        assertFalse(
            ProfileSync.isRemoteStateNewer(
                remoteVersion = 0, remoteTs = 0,
                localVersion = 0, localTs = 5_000
            )
        )
    }

    // ---------- applyRemoteChildJsonAsync на dirty-профиле ----------

    @Test
    fun `remote approve is applied over stale dirty pending`() = runBlocking {
        // Ребёнок: локальный PENDING v1, профиль dirty (push в полёте/неудачен).
        val local = child().copy(
            closetStatus = QuestStatus.PENDING,
            stateVersion = 1,
            stateUpdatedAt = 5_000
        )
        AppSharedState.children["user_1"] = local
        SyncStatus.markDirty("user_1")

        // Родитель в облаке: APPROVED→READY_TO_CLAIM, версия v2.
        val remote = local.copy(
            closetStatus = QuestStatus.READY_TO_CLAIM,
            closetPhotoUrl = null,
            stateVersion = 2,
            stateUpdatedAt = 6_000
        )
        val remoteJson = AppStateStorage.serializeSingleChild(remote)

        AppStateStorage.applyRemoteChildJsonAsync(
            context, "user_1", remoteJson,
            parentUserId = null, status = null, authUserId = null,
            remoteUpdatedAt = null
        )

        val applied = AppSharedState.children["user_1"]!!
        assertEquals(QuestStatus.READY_TO_CLAIM, applied.closetStatus)
        assertEquals(2L, applied.stateVersion)
        // Применённая удалённая версия снимает dirty — старый снапшот
        // не будет отправлен retryDirtyChildren поверх approve.
        assertFalse(SyncStatus.isChildDirty("user_1"))
    }

    @Test
    fun `stale remote is not applied over newer dirty local`() = runBlocking {
        // У ребёнка есть неподтверждённая локальная правка v3,
        // а в облаке лежит более старая версия v2.
        val local = child().copy(
            closetStatus = QuestStatus.PENDING,
            stateVersion = 3,
            stateUpdatedAt = 9_000
        )
        AppSharedState.children["user_1"] = local
        SyncStatus.markDirty("user_1")

        val staleRemote = local.copy(
            closetStatus = QuestStatus.AVAILABLE,
            stateVersion = 2,
            stateUpdatedAt = 1_000
        )
        AppStateStorage.applyRemoteChildJsonAsync(
            context, "user_1",
            AppStateStorage.serializeSingleChild(staleRemote),
            parentUserId = null, status = null, authUserId = null,
            remoteUpdatedAt = null
        )

        val kept = AppSharedState.children["user_1"]!!
        assertEquals(QuestStatus.PENDING, kept.closetStatus)
        assertEquals(3L, kept.stateVersion)
        assertTrue(SyncStatus.isChildDirty("user_1"))
    }

    @Test
    fun `stateVersion survives dto round trip`() {
        val original = child().copy(stateVersion = 42, stateUpdatedAt = 123)
        val restored = ChildProfileState.fromDto(original.toDto())
        assertEquals(42L, restored.stateVersion)
        assertEquals(123L, restored.stateUpdatedAt)
    }
}
