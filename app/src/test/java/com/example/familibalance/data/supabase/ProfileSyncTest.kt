package com.example.familibalance.data.supabase

import android.content.Context
import com.example.familibalance.data.model.ChildProfileState
import com.example.familibalance.data.repository.SyncStatus
import com.example.familibalance.data.storage.AppSharedState
import com.example.familibalance.data.storage.AppStateStorage
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
 * Тесты логики синхронизации: видимость строк family_profiles,
 * классификация RLS-ошибок и last-writer-wins при применении
 * удалённого состояния (merge/конфликты/echo-дубликаты).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileSyncTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AppSharedState.clearChildren()
        SyncStatus.attach(context)
    }

    @After
    fun tearDown() {
        AppSharedState.clearChildren()
        listOf("vis_u1", "lww_clean", "lww_dirty_old", "lww_dirty_new", "lww_echo").forEach {
            SyncStatus.markClean(it)
        }
    }

    // ---------- isRowVisibleToUser ----------

    private fun row(
        userId: String,
        parentUserId: String? = null,
        parentEmail: String? = null,
        authUserId: String? = null,
        stateJson: String = "{}"
    ) = FamilyProfileRowTs(
        user_id = userId,
        state_json = stateJson,
        parentUserId = parentUserId,
        parentEmail = parentEmail,
        authUserId = authUserId
    )

    @Test
    fun `guest without session sees nothing`() {
        val r = row("child1", parentUserId = "parent1")
        assertFalse(ProfileSync.isRowVisibleToUser(r, uid = null, email = null))
    }

    @Test
    fun `own profile is always visible`() {
        val r = row("anon_child", parentUserId = "parent1")
        assertTrue(ProfileSync.isRowVisibleToUser(r, uid = "anon_child", email = null))
    }

    @Test
    fun `child profile visible to its parent by parentUserId`() {
        val r = row("child1", parentUserId = "parent1")
        assertTrue(ProfileSync.isRowVisibleToUser(r, uid = "parent1", email = "p@x.com"))
    }

    @Test
    fun `child device sees own profile by authUserId`() {
        val r = row("child1", parentUserId = "parent1", authUserId = "anon_session")
        assertTrue(ProfileSync.isRowVisibleToUser(r, uid = "anon_session", email = null))
    }

    @Test
    fun `profile bound to another parent is hidden`() {
        val r = row("child1", parentUserId = "parent1")
        assertFalse(ProfileSync.isRowVisibleToUser(r, uid = "parent2", email = "p2@x.com"))
    }

    @Test
    fun `pending request visible by parentEmail column`() {
        val r = row("pending1", parentEmail = "Mom@X.com")
        assertTrue(ProfileSync.isRowVisibleToUser(r, uid = "parent1", email = "mom@x.com"))
        assertFalse(ProfileSync.isRowVisibleToUser(r, uid = "parent1", email = "other@x.com"))
    }

    @Test
    fun `pending request visible by parentEmail inside state_json`() {
        val r = row("pending1", stateJson = """{"parentEmail":"dad@x.com"}""")
        assertTrue(ProfileSync.isRowVisibleToUser(r, uid = "parent1", email = "DAD@x.com"))
        assertFalse(ProfileSync.isRowVisibleToUser(r, uid = "parent1", email = null))
    }

    @Test
    fun `unbound profile without any email match is hidden`() {
        val r = row("orphan1", stateJson = """{"name":"Kid"}""")
        assertFalse(ProfileSync.isRowVisibleToUser(r, uid = "parent1", email = "p@x.com"))
    }

    // ---------- isRlsOrAuthError ----------

    @Test
    fun `rls and auth errors are classified`() {
        assertTrue(ProfileSync.isRlsOrAuthError(Exception("new row violates row-level security policy")))
        assertTrue(ProfileSync.isRlsOrAuthError(Exception("HTTP 401 Unauthorized")))
        assertTrue(ProfileSync.isRlsOrAuthError(Exception("JWT expired")))
        assertFalse(ProfileSync.isRlsOrAuthError(Exception("connection timeout")))
        assertFalse(ProfileSync.isRlsOrAuthError(Exception("duplicate key value")))
    }

    // ---------- applyRemoteChildJsonAsync: merge / LWW / echo ----------

    private fun child(id: String, ts: Long, balance: Int = 0) = ChildProfileState(
        id = id, name = "Kid $id", avatar = "🧒",
        balance = balance, stateUpdatedAt = ts
    )

    @Test
    fun `remote update applied to clean profile`() = runBlocking {
        val local = child("lww_clean", ts = 1_000, balance = 50)
        AppSharedState.putChild(local)

        val remote = child("lww_clean", ts = 2_000, balance = 99)
        val json = AppStateStorage.serializeSingleChild(remote)
        AppStateStorage.applyRemoteChildJsonAsync(
            context, "lww_clean", json,
            remoteUpdatedAt = "2026-01-02T00:00:00+00:00"
        )

        assertEquals(99, AppSharedState.children["lww_clean"]!!.balance)
        assertFalse(SyncStatus.isChildDirty("lww_clean"))
    }

    @Test
    fun `remote update skipped when dirty local is newer`() = runBlocking {
        val local = child("lww_dirty_old", ts = 5_000, balance = 50)
        AppSharedState.putChild(local)
        SyncStatus.markDirty("lww_dirty_old")

        // Удалённая версия старше локальной — локальные правки в приоритете
        val remote = child("lww_dirty_old", ts = 1_000, balance = 99)
        val json = AppStateStorage.serializeSingleChild(remote)
        AppStateStorage.applyRemoteChildJsonAsync(
            context, "lww_dirty_old", json,
            remoteUpdatedAt = "2026-01-03T00:00:00+00:00"
        )

        assertEquals(50, AppSharedState.children["lww_dirty_old"]!!.balance)
        assertTrue(SyncStatus.isChildDirty("lww_dirty_old"))
    }

    @Test
    fun `remote update applied when dirty local is older and clears dirty`() = runBlocking {
        val local = child("lww_dirty_new", ts = 1_000, balance = 50)
        AppSharedState.putChild(local)
        SyncStatus.markDirty("lww_dirty_new")

        // Удалённая версия новее — побеждает last-writer-wins,
        // dirty снимается, чтобы старый снапшот не перезаписал approve/reject
        val remote = child("lww_dirty_new", ts = 9_000, balance = 77)
        val json = AppStateStorage.serializeSingleChild(remote)
        AppStateStorage.applyRemoteChildJsonAsync(
            context, "lww_dirty_new", json,
            remoteUpdatedAt = "2026-01-04T00:00:00+00:00"
        )

        assertEquals(77, AppSharedState.children["lww_dirty_new"]!!.balance)
        assertFalse(SyncStatus.isChildDirty("lww_dirty_new"))
    }

    @Test
    fun `echo of already synced version is skipped`() = runBlocking {
        val local = child("lww_echo", ts = 3_000, balance = 10)
        AppSharedState.putChild(local)

        // Серверный updated_at уже применён — повторное событие (echo push'а)
        // не должно менять состояние и дёргать UI.
        SyncStatus.setCloudVersion("lww_echo", "2026-01-05T00:00:00+00:00")
        val remote = child("lww_echo", ts = 4_000, balance = 88)
        val json = AppStateStorage.serializeSingleChild(remote)
        AppStateStorage.applyRemoteChildJsonAsync(
            context, "lww_echo", json,
            remoteUpdatedAt = "2026-01-05T00:00:00+00:00"
        )

        assertEquals(10, AppSharedState.children["lww_echo"]!!.balance)
    }
}
