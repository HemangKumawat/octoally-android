package com.octoally.core.data.skills

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the decay math: 12h half-life, score = count * exp(-ln2 * ageHours / 12).
 * Pure-JVM — no Robolectric required, since [SkillRanking.score] is a pure function.
 */
class SkillRankingTest {

    @Test
    fun `half-life 12h halves the count`() {
        val now = 1_700_000_000_000L
        val twelveHoursAgo = now - 12L * 3_600_000L
        val score = SkillRanking.score(count = 10, lastUsedAt = twelveHoursAgo, nowMs = now)
        // 10 * 0.5 = 5.0
        assertEquals(5.0, score, 0.05)
    }

    @Test
    fun `fresh use has score equal to count`() {
        val now = 1_700_000_000_000L
        val score = SkillRanking.score(count = 7, lastUsedAt = now, nowMs = now)
        assertEquals(7.0, score, 1e-9)
    }

    @Test
    fun `24h old halves twice`() {
        val now = 1_700_000_000_000L
        val dayAgo = now - 24L * 3_600_000L
        val score = SkillRanking.score(count = 8, lastUsedAt = dayAgo, nowMs = now)
        // 8 * 0.25 = 2.0
        assertEquals(2.0, score, 0.02)
    }

    @Test
    fun `future timestamps are clamped to zero age`() {
        val now = 1_700_000_000_000L
        val futureTs = now + 60_000L // clock skew
        val score = SkillRanking.score(count = 3, lastUsedAt = futureTs, nowMs = now)
        assertEquals(3.0, score, 1e-9)
    }
}
