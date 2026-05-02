package com.octoally.core.data.skills

import kotlin.math.exp
import kotlin.math.ln

/** A ranked skill suggestion: higher [score] = more relevant. */
data class RankedSkill(
    val name: String,
    val score: Double,
    val count: Int,
    val lastUsedAt: Long
)

internal object SkillRanking {
    /** Decay half-life in hours. After 12h, a skill's score is halved. */
    const val HALF_LIFE_HOURS: Double = 12.0
    private val LN2: Double = ln(2.0)
    private const val MS_PER_HOUR: Double = 3_600_000.0

    /**
     * Weighted score: `count * exp(-ln(2) * ageHours / halfLife)`.
     * Pure function — no time source here; caller passes `nowMs`.
     */
    fun score(count: Int, lastUsedAt: Long, nowMs: Long): Double {
        val ageHours = (nowMs - lastUsedAt).coerceAtLeast(0L) / MS_PER_HOUR
        return count * exp(-LN2 * ageHours / HALF_LIFE_HOURS)
    }
}
