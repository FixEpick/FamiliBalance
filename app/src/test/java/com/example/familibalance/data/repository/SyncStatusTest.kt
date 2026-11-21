package com.example.familibalance.data.repository

import android.content.Context
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
 * Тесты [SyncStatus]: жизненный цикл dirty/clean, флаг isSynced
 * и сравнение серверных timestamps (last-writer-wins).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncStatusTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SyncStatus.attach(context)
        listOf("s1", "s2", "s3").forEach { SyncStatus.markClean(it) }
    }

    @After
    fun tearDown() {
        listOf("s1", "s2", "s3").forEach { SyncStatus.markClean(it) }
    }

    @Test
    fun `markDirty sets isSynced false and isChildDirty true`() {
        assertTrue(SyncStatus.isSynced.value)
        SyncStatus.markDirty("s1")
        assertFalse(SyncStatus.isSynced.value)
        assertTrue(SyncStatus.isChildDirty("s1"))
        assertTrue(SyncStatus.hasDirtyChildren())
        assertTrue(SyncStatus.dirtyChildIdsSnapshot().contains("s1"))
    }

    @Test
    fun `markClean returns true only when last dirty removed`() {
        SyncStatus.markDirty("s1")
        SyncStatus.markDirty("s2")
        assertFalse(SyncStatus.markClean("s1"))
        assertFalse(SyncStatus.isSynced.value)
        assertTrue(SyncStatus.markClean("s2"))
        assertTrue(SyncStatus.isSynced.value)
        assertFalse(SyncStatus.hasDirtyChildren())
    }

    @Test
    fun `markClean on clean child keeps isSynced true`() {
        assertTrue(SyncStatus.markClean("s3"))
        assertTrue(SyncStatus.isSynced.value)
    }

    @Test
    fun `isTsNewer compares ISO timestamps correctly`() {
        assertTrue(
            SyncStatus.isTsNewer(
                "2026-01-02T10:00:00+00:00",
                "2026-01-02T09:00:00+00:00"
            )
        )
        assertFalse(
            SyncStatus.isTsNewer(
                "2026-01-02T09:00:00+00:00",
                "2026-01-02T10:00:00+00:00"
            )
        )
        assertFalse(
            SyncStatus.isTsNewer(
                "2026-01-02T10:00:00+00:00",
                "2026-01-02T10:00:00+00:00"
            )
        )
        // Разная дробная часть секунд
        assertTrue(
            SyncStatus.isTsNewer(
                "2026-01-02T10:00:00.500+00:00",
                "2026-01-02T10:00:00.100+00:00"
            )
        )
    }

    @Test
    fun `isTsNewer falls back to string compare on invalid input`() {
        assertTrue(SyncStatus.isTsNewer("b", "a"))
        assertFalse(SyncStatus.isTsNewer("a", "b"))
    }

    @Test
    fun `cloudVersion keeps only newer server timestamp`() {
        SyncStatus.setCloudVersion("s1", "2026-01-01T00:00:00+00:00")
        assertEquals("2026-01-01T00:00:00+00:00", SyncStatus.cloudVersion("s1"))

        // Более старая версия не перезаписывает
        SyncStatus.setCloudVersion("s1", "2025-12-31T00:00:00+00:00")
        assertEquals("2026-01-01T00:00:00+00:00", SyncStatus.cloudVersion("s1"))

        // Более новая — перезаписывает
        SyncStatus.setCloudVersion("s1", "2026-01-02T00:00:00+00:00")
        assertEquals("2026-01-02T00:00:00+00:00", SyncStatus.cloudVersion("s1"))

        // Пустой/blank игнорируется
        SyncStatus.setCloudVersion("s1", "")
        assertEquals("2026-01-02T00:00:00+00:00", SyncStatus.cloudVersion("s1"))
    }
}
