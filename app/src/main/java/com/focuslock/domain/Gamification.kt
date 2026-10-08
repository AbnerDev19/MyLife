package com.focuslock.domain

import java.time.LocalDate

object Gamification {
    private val thresholds = listOf(0, 500, 1200, 2500, 4500, 7500, 12000, 20000)

    fun level(xp: Int): Int = thresholds.indexOfLast { xp >= it } + 1

    fun levelStart(xp: Int): Int = thresholds[level(xp) - 1]

    fun nextLevelXp(xp: Int): Int? = thresholds.getOrNull(level(xp))

    fun levelProgress(xp: Int): Float {
        val next = nextLevelXp(xp) ?: return 1f
        val start = levelStart(xp)
        return (xp - start).toFloat() / (next - start)
    }

    /** XP de um dia concluído: 100 base, +500 a cada 7 dias seguidos, +2000 a cada 30. */
    fun dayXp(streak: Int): Int = 100 + when {
        streak > 0 && streak % 30 == 0 -> 2000
        streak > 0 && streak % 7 == 0 -> 500
        else -> 0
    }

    fun currentStreak(dates: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in dates) today else today.minusDays(1)
        var count = 0
        while (d in dates) { count++; d = d.minusDays(1) }
        return count
    }

    fun bestStreak(dates: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in dates.sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        return best
    }
}
