package com.chrispsz.rotinacheck.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-logic tests for the OTA version comparison. These encode the CONTRACT:
 * a broken or weird tag must never nag, and a real release must never be
 * swallowed.
 */
class SemVerTest {

    // ---- Basics -----------------------------------------------------------------

    @Test
    fun `same version is not newer`() {
        assertFalse(SemVer.isNewer("0.4.0", "0.4.0"))
        assertFalse(SemVer.isNewer("v0.4.0", "0.4.0"))
        assertFalse(SemVer.isNewer("0.4.0", "v0.4.0"))
    }

    @Test
    fun `older remote is not newer`() {
        assertFalse(SemVer.isNewer("0.3.9", "0.4.0"))
        assertFalse(SemVer.isNewer("v0.3.0", "v0.4.0"))
    }

    @Test
    fun `newer patch minor and major`() {
        assertTrue(SemVer.isNewer("0.4.1", "0.4.0"))
        assertTrue(SemVer.isNewer("0.5.0", "0.4.9"))
        assertTrue(SemVer.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun `v prefix is optional on both sides`() {
        assertTrue(SemVer.isNewer("v1.2.3", "1.2.2"))
        assertTrue(SemVer.isNewer("1.2.3", "v1.2.2"))
        assertFalse(SemVer.isNewer("v1.2.2", "v1.2.3"))
    }

    // ---- Two-part and irregular tags ---------------------------------------------

    @Test
    fun `two-part tag defaults patch to zero`() {
        assertTrue(SemVer.isNewer("0.5", "0.4.99"))
        assertFalse(SemVer.isNewer("0.4", "0.4.0"))
        assertTrue(SemVer.isNewer("0.5", "0.4"))
    }

    // ---- Prerelease suffixes (defense-in-depth: releases/latest already
    //      excludes prereleases, but a mislabeled one must not nag either) ----

    @Test
    fun `prerelease suffix is older than the bare number`() {
        // v0.4.1-test vs installed 0.4.1 stable → NOT newer (equal base, pre)
        assertFalse(SemVer.isNewer("0.4.1-test", "0.4.1"))
        assertFalse(SemVer.isNewer("v0.4.1-rc1", "v0.4.1"))
    }

    @Test
    fun `stable beats prerelease current`() {
        // installed 0.4.1-test, remote 0.4.1 stable → upgrade
        assertTrue(SemVer.isNewer("0.4.1", "0.4.1-test"))
    }

    @Test
    fun `prerelease of a newer base IS newer`() {
        // A mislabeled prerelease of a NEW version still looks newer — the
        // release workflow marks `-test` tags as prerelease, which is the
        // primary filter; this only documents the second-order behavior.
        assertTrue(SemVer.isNewer("0.5.0-test", "0.4.9"))
    }

    @Test
    fun `prerelease with higher base than installed IS newer`() {
        // Base 0.4.1 > 0.4.0 dominates the prerelease suffix.
        assertTrue(SemVer.isNewer("0.4.1-test", "0.4.0"))
    }

    // ---- Robustness: garbage never nags -------------------------------------------

    @Test
    fun `unparseable remote is not newer`() {
        assertFalse(SemVer.isNewer("", "0.4.0"))
        assertFalse(SemVer.isNewer("abc", "0.4.0"))
        assertFalse(SemVer.isNewer("release-candidate", "0.4.0"))
        assertFalse(SemVer.isNewer("..", "0.4.0"))
    }

    @Test
    fun `unparseable current never shows updates`() {
        // Defensive: if the installed version string is garbage, stay silent.
        assertFalse(SemVer.isNewer("0.5.0", ""))
        assertFalse(SemVer.isNewer("0.5.0", "unknown"))
    }

    @Test
    fun `overflowing numbers are treated as garbage`() {
        assertFalse(SemVer.isNewer("99999999999999.0.0", "0.4.0"))
    }

    @Test
    fun `whitespace is tolerated`() {
        assertTrue(SemVer.isNewer(" 0.5.0 ", "0.4.0"))
        assertTrue(SemVer.isNewer("v0.5.0", " 0.4.0 "))
    }

    // ---- Multi-digit segments ------------------------------------------------------

    @Test
    fun `multi-digit segments compare numerically not lexicographically`() {
        assertTrue(SemVer.isNewer("0.10.0", "0.9.9"))
        assertFalse(SemVer.isNewer("0.9.9", "0.10.0"))
        assertTrue(SemVer.isNewer("1.0.10", "1.0.9"))
    }
}
