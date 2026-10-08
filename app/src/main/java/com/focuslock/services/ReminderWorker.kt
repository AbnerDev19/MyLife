package com.focuslock.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focuslock.data.AppDatabase
import com.focuslock.domain.Gamification
import com.focuslock.domain.Protection
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** No máximo uma notificação por dia, por volta das 20h. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        if (db.settingsDao().getNow()?.notifications == false) return Result.success()
        val today = LocalDate.now()
        val dates = db.progressDao().allNow().map { LocalDate.parse(it.date) }.toSet()
        val streak = Gamification.currentStreak(dates, today)
        val best = Gamification.bestStreak(dates)
        val ch = db.challengeDao().activeNow()
        val done = today in dates
        val daysLeft = if (ch != null && Protection.isActive(ch)) Duration.between(LocalDateTime.now(), Protection.endOf(ch)).toDays() else -1L

        val msg = when {
            daysLeft == 3L -> "Seu desafio termina em 3 dias."
            !done && streak > 0 && streak == best && best >= 3 -> "Você está a 1 dia de bater sua maior sequência."
            !done && streak > 0 -> "Falta concluir hoje para manter sua sequência de $streak dias."
            else -> null
        }
        if (msg != null) Notifier.send(applicationContext, 2, msg)
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val now = LocalDateTime.now()
            var first = now.toLocalDate().atTime(LocalTime.of(20, 0))
            if (!first.isAfter(now)) first = first.plusDays(1)
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("reminder", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
