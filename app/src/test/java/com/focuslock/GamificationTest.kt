package com.focuslock

import com.focuslock.domain.Gamification
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class GamificationTest {
    private val d = LocalDate.of(2026, 10, 10)

    @Test fun levelsFollowThresholds() {
        assertEquals(1, Gamification.level(0))
        assertEquals(2, Gamification.level(500))
        assertEquals(2, Gamification.level(1199))
        assertEquals(3, Gamification.level(1200))
    }

    @Test fun dayXpHasStreakBonuses() {
        assertEquals(100, Gamification.dayXp(3))
        assertEquals(600, Gamification.dayXp(7))
        assertEquals(2100, Gamification.dayXp(30))
    }

    @Test fun currentStreakCountsBackFromTodayOrYesterday() {
        val dates = setOf(d, d.minusDays(1), d.minusDays(2), d.minusDays(5))
        assertEquals(3, Gamification.currentStreak(dates, d))
        assertEquals(2, Gamification.currentStreak(setOf(d.minusDays(1), d.minusDays(2)), d))
        assertEquals(0, Gamification.currentStreak(setOf(d.minusDays(3)), d))
    }

    @Test fun bestStreakFindsLongestRun() {
        val dates = setOf(d, d.plusDays(1), d.plusDays(2), d.plusDays(4), d.plusDays(5))
        assertEquals(3, Gamification.bestStreak(dates))
    }
}
