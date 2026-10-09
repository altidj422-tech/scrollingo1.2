package com.scrollingo.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QuizProgress.Snapshot] is what the status-bar notification renders. The
 * division in [QuizProgress.Snapshot.todayPercent] runs before the learner has
 * answered anything, so the zero case has to stay safe.
 */
class QuizProgressSnapshotTest {

    private fun snap(correct: Int, total: Int, streak: Int = 0, best: Int = 0) =
        QuizProgress.Snapshot(correct, total, streak, best)

    @Test
    fun `percent is zero before the first answer, never a divide by zero`() {
        assertEquals(0, snap(0, 0).todayPercent)
    }

    @Test
    fun `percent reports plain accuracy`() {
        assertEquals(100, snap(4, 4).todayPercent)
        assertEquals(50, snap(2, 4).todayPercent)
        assertEquals(0, snap(0, 3).todayPercent)
    }

    @Test
    fun `percent rounds to the nearest whole number`() {
        assertEquals(33, snap(1, 3).todayPercent)   // 33.33 -> 33
        assertEquals(67, snap(2, 3).todayPercent)   // 66.67 -> 67
        assertEquals(86, snap(6, 7).todayPercent)   // 85.71 -> 86
    }

    @Test
    fun `percent stays inside 0 to 100`() {
        for (total in 1..40) {
            for (correct in 0..total) {
                val p = snap(correct, total).todayPercent
                assertTrue("$correct/$total gave $p", p in 0..100)
            }
        }
    }

    @Test
    fun `hasActivity is false only on a completely blank record`() {
        assertFalse(snap(0, 0, streak = 0, best = 0).hasActivity)
        assertTrue(snap(0, 1).hasActivity)                 // answered today
        assertTrue(snap(0, 0, streak = 2).hasActivity)     // mid-run
        assertTrue(snap(0, 0, best = 9).hasActivity)       // history from earlier days
    }
}
