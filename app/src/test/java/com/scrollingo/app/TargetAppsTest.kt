package com.scrollingo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accessibility service matches the foreground app by package name, so a
 * missing or misspelled package means Scrollingo silently stops firing quizzes
 * on that app. These tests pin the mapping down.
 */
class TargetAppsTest {

    @Test
    fun `tiktok covers all three regional package names`() {
        assertEquals(
            setOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.ss.android.ugc.aweme"
            ),
            TargetApps.TIKTOK.packages
        )
    }

    @Test
    fun `instagram maps to the single android package`() {
        assertEquals(setOf("com.instagram.android"), TargetApps.INSTAGRAM.packages)
    }

    @Test
    fun `packagesFor resolves a single id`() {
        assertEquals(TargetApps.INSTAGRAM.packages, TargetApps.packagesFor(setOf("instagram")))
    }

    @Test
    fun `packagesFor unions every selected app`() {
        val all = TargetApps.packagesFor(TargetApps.ALL_IDS)
        assertEquals(4, all.size)
        assertTrue(all.containsAll(TargetApps.TIKTOK.packages))
        assertTrue(all.containsAll(TargetApps.INSTAGRAM.packages))
    }

    @Test
    fun `packagesFor ignores unknown ids instead of throwing`() {
        assertEquals(emptySet<String>(), TargetApps.packagesFor(setOf("youtube")))
        assertEquals(
            TargetApps.TIKTOK.packages,
            TargetApps.packagesFor(setOf("tiktok", "snapchat"))
        )
    }

    @Test
    fun `an empty selection watches nothing`() {
        assertTrue(TargetApps.packagesFor(emptySet()).isEmpty())
    }

    @Test
    fun `ALL_IDS stays in sync with ALL and every app is labelled`() {
        assertEquals(TargetApps.ALL.map { it.id }.toSet(), TargetApps.ALL_IDS)
        assertEquals(TargetApps.ALL.size, TargetApps.ALL_IDS.size) // no duplicate ids
        assertTrue(TargetApps.ALL.all { it.label.isNotBlank() && it.packages.isNotEmpty() })
    }
}
