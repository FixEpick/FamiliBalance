package com.example.familibalance.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `patch version is newer`() {
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun `double digit segment beats single digit`() {
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.10"))
    }

    @Test
    fun `leading v is ignored`() {
        assertFalse(VersionComparator.isNewer("v1.0.1", "1.0.1"))
        assertTrue(VersionComparator.isNewer("1.0.1", "v1.0.2"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(VersionComparator.isNewer("1.0.1", "1.0.1"))
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun `pre-release suffix is ignored`() {
        // "1.0.1-alpha" парсится как 1.0.1 — не новее 1.0.1
        assertFalse(VersionComparator.isNewer("1.0.1", "1.0.1-alpha"))
        assertTrue(VersionComparator.isNewer("1.0.1-alpha", "1.0.2"))
    }

    @Test
    fun `missing segments treated as zero`() {
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"))
        assertTrue(VersionComparator.isNewer("1.0", "1.0.1"))
    }

    @Test
    fun `major version bump`() {
        assertTrue(VersionComparator.isNewer("1.9.9", "2.0.0"))
        assertFalse(VersionComparator.isNewer("2.0.0", "1.9.9"))
    }
}
