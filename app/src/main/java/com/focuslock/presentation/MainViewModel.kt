package com.focuslock.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focuslock.data.AchievementEntity
import com.focuslock.data.AppDatabase
import com.focuslock.data.AppSettingsEntity
import com.focuslock.data.BlockedAppEntity
import com.focuslock.data.BlockedDomainEntity
import com.focuslock.data.ChallengeEntity
import com.focuslock.data.DailyProgressEntity
import com.focuslock.data.GoalEntity
import com.focuslock.data.HabitEntity
import com.focuslock.data.ActivityEntity
import com.focuslock.data.AttributeEntity
import com.focuslock.data.SubjectEntity
import com.focuslock.data.StudySessionEntity
import com.focuslock.data.HistoryEntity
import com.focuslock.data.XpTransactionEntity
import com.focuslock.domain.Achievements
import com.focuslock.domain.Gamification
import com.focuslock.domain.Protection
import com.focuslock.services.Notifier
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val loaded: Boolean = false,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val xp: Int = 0,
    val days: Map<LocalDate, Int> = emptyMap(),
    val challenge: ChallengeEntity? = null,
    val challengeDone: Int = 0,
    val goals: List<GoalEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val activities: List<ActivityEntity> = emptyList(),
    val attributes: List<AttributeEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val studySessions: List<StudySessionEntity> = emptyList(),
    val history: List<HistoryEntity> = emptyList(),
    val doneToday: Boolean = false,
    val settings: AppSettingsEntity = AppSettingsEntity(),
    val blockedApps: List<BlockedAppEntity> = emptyList(),
    val domains: List<BlockedDomainEntity> = emptyList(),
    val blocks: Map<LocalDate, Int> = emptyMap(),
    val achievements: Set<String> = emptySet(),
    val hardcoreActive: Boolean = false,
    val protectionActive: Boolean = false
)

private data class Core(
    val streak: Int, val best: Int, val xp: Int, val days: Map<LocalDate, Int>,
    val challenge: ChallengeEntity?, val done: Int, val goals: List<GoalEntity>, val habits: List<HabitEntity>,
    val activities: List<ActivityEntity>, val attributes: List<AttributeEntity>, val subjects: List<SubjectEntity>,
    val studySessions: List<StudySessionEntity>, val history: List<HistoryEntity>
)

private data class Extra(
    val apps: List<BlockedAppEntity>, val domains: List<BlockedDomainEntity>, val settings: AppSettingsEntity,
    val blocks: Map<LocalDate, Int>, val achievements: Set<String>
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    private val core = combine(db.progressDao().all(), db.xpDao().total(), db.challengeDao().active(), db.goalDao().all()) { progress, xp, ch, goals ->
        val days = progress.associate { LocalDate.parse(it.date) to it.xp }
        val done = if (ch == null) 0 else {
            val start = LocalDate.parse(ch.startDate)
            minOf(days.keys.count { !it.isBefore(start) }, ch.totalDays)
        }
        Core(Gamification.currentStreak(days.keys, LocalDate.now()), Gamification.bestStreak(days.keys), xp, days, ch, done, goals)
    }

    private val extra = combine(
        db.blockedAppDao().all(), db.blockedDomainDao().all(), db.settingsDao().observe(),
        db.blockLogDao().all(), db.achievementDao().all()
    ) { apps, domains, st, log, ach ->
        Extra(apps, domains, st ?: AppSettingsEntity(), log.associate { LocalDate.parse(it.date) to it.count }, ach.map { it.id }.toSet())
    }

    private val routine = combine(
        db.habitDao().all(), db.activityDao().all(), db.attributeDao().all(), db.subjectDao().all(), db.studySessionDao().all(), db.historyDao().recent()
    ) { habits, activities, attributes, subjects, sessions, history ->
        listOf(habits, activities, attributes, subjects, sessions, history)
    }

    val state: StateFlow<UiState> = combine(core, extra, routine) { c, e, r ->
        @Suppress("UNCHECKED_CAST") val habits = r[0] as List<HabitEntity>
        @Suppress("UNCHECKED_CAST") val activities = r[1] as List<ActivityEntity>
        @Suppress("UNCHECKED_CAST") val attributes = r[2] as List<AttributeEntity>
        @Suppress("UNCHECKED_CAST") val subjects = r[3] as List<SubjectEntity>
        @Suppress("UNCHECKED_CAST") val sessions = r[4] as List<StudySessionEntity>
        @Suppress("UNCHECKED_CAST") val history = r[5] as List<HistoryEntity>
        UiState(
            loaded = true, streak = c.streak, bestStreak = c.best, xp = c.xp, days = c.days,
            challenge = c.challenge, challengeDone = c.done, goals = c.goals, habits = habits, activities = activities,
            attributes = attributes, subjects = subjects, studySessions = sessions, history = history,
            doneToday = LocalDate.now() in c.days, settings = e.settings, blockedApps = e.apps, domains = e.domains, blocks = e.blocks, achievements = e.achievements,
            hardcoreActive = Protection.hardcoreActive(e.settings), protectionActive = Protection.isActive(c.challenge)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            state.collect { s ->
                if (!s.loaded) return@collect
                val earned = Achievements.earned(s.bestStreak, s.days.size, s.goals.count { it.done }, s.xp, s.blocks.values.sum())
                earned.filter { it !in s.achievements }.forEach {
                    db.achievementDao().insert(AchievementEntity(it, LocalDate.now().toString()))
                }
            }
        }
    }

    private fun edit(f: (AppSettingsEntity) -> AppSettingsEntity) {
        viewModelScope.launch {
            val cur = db.settingsDao().getNow() ?: AppSettingsEntity()
            db.settingsDao().upsert(f(cur))
        }
    }

    fun checkIn() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val dates = state.value.days.keys
            if (today in dates) return@launch
            val gain = Gamification.dayXp(Gamification.currentStreak(dates + today, today))
            db.progressDao().upsert(DailyProgressEntity(today.toString(), gain))
            db.xpDao().insert(XpTransactionEntity(date = today.toString(), amount = gain, reason = "Dia concluído"))
            Notifier.send(getApplication(), 1, "Você completou mais um dia.")
        }
    }

    fun createChallenge(name: String, days: Int) {
        viewModelScope.launch {
            db.challengeDao().insert(ChallengeEntity(name = name, startDate = LocalDate.now().toString(), totalDays = days))
        }
    }

    fun addGoal(title: String, xp: Int = 50) {
        viewModelScope.launch { db.goalDao().insert(GoalEntity(title = title, xp = xp.coerceAtLeast(1))) }
    }

    fun completeGoal(goal: GoalEntity) {
        if (goal.done) return
        viewModelScope.launch {
            db.goalDao().update(goal.copy(done = true))
            db.xpDao().insert(XpTransactionEntity(date = LocalDate.now().toString(), amount = goal.xp, reason = "Objetivo: " + goal.title))
            history("Objetivo concluído: ${goal.title} (+${goal.xp} XP)")
        }
    }

    fun finishOnboarding(name: String, challenge: String, days: Int) {
        viewModelScope.launch {
            val cur = db.settingsDao().getNow() ?: AppSettingsEntity()
            db.settingsDao().upsert(cur.copy(userName = name.trim(), onboarded = true))
            if (challenge.isNotBlank() && state.value.challenge == null) {
                db.challengeDao().insert(ChallengeEntity(name = challenge.trim(), startDate = LocalDate.now().toString(), totalDays = days.coerceAtLeast(1)))
            }
        }
    }

    fun setName(name: String) = edit { it.copy(userName = name.trim()) }

    fun setNotifications(on: Boolean) = edit { it.copy(notifications = on) }

    /** Alterações de proteção são recusadas enquanto o modo Hardcore estiver ativo. */
    fun setProtection(f: (AppSettingsEntity) -> AppSettingsEntity) {
        if (state.value.hardcoreActive) return
        edit(f)
    }

    fun toggleApp(pkg: String, label: String) {
        viewModelScope.launch {
            val blocked = state.value.blockedApps.any { it.packageName == pkg }
            if (blocked) {
                if (state.value.hardcoreActive) return@launch
                db.blockedAppDao().delete(pkg)
            } else {
                db.blockedAppDao().insert(BlockedAppEntity(pkg, label))
            }
        }
    }

    fun addDomain(raw: String) {
        val d = raw.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore('/').substringBefore('?').trim()
        if (!d.contains('.')) return
        viewModelScope.launch { db.blockedDomainDao().insert(BlockedDomainEntity(d)) }
    }

    fun removeDomain(domain: String) {
        if (state.value.hardcoreActive) return
        viewModelScope.launch { db.blockedDomainDao().delete(domain) }
    }

    private fun history(text: String) { viewModelScope.launch { db.historyDao().insert(HistoryEntity(text = text)) } }

    fun addRoutineGoal(title: String, xp: Int) { addGoal(title, xp); history("Nova missão: $title") }

    fun toggleHabit(habit: HabitEntity) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            if (habit.completedDate == today) return@launch
            val streak = habit.streak + 1
            db.habitDao().update(habit.copy(streak = streak, completedDate = today))
            db.xpDao().insert(XpTransactionEntity(date = today, amount = 10, reason = "Hábito: ${habit.name}"))
            history("Hábito concluído: ${habit.name} (+10 XP)")
        }
    }

    fun addHabit(name: String, attrId: Long?) { viewModelScope.launch { db.habitDao().insert(HabitEntity(name = name, attrId = attrId)); history("Novo hábito: $name") } }
    fun addActivity(name: String, dueAt: Long, xp: Int, attrId: Long?) { viewModelScope.launch { db.activityDao().insert(ActivityEntity(name=name, dueAt=dueAt, xp=xp, attrId=attrId)); history("Nova atividade: $name") } }
    fun completeActivity(a: ActivityEntity) {
        if (a.completed || a.failed) return
        viewModelScope.launch {
            db.activityDao().update(a.copy(completed=true))
            db.xpDao().insert(XpTransactionEntity(date=LocalDate.now().toString(), amount=a.xp, reason="Atividade: ${a.name}"))
            history("Atividade concluída: ${a.name} (+${a.xp} XP)")
        }
    }
    fun addAttribute(name: String) { viewModelScope.launch { db.attributeDao().insert(AttributeEntity(name=name)); history("Novo atributo: $name") } }
    fun addSubject(name: String) { viewModelScope.launch { db.subjectDao().insert(SubjectEntity(name=name)); history("Nova matéria: $name") } }
    fun addStudy(minutes: Int, subject: SubjectEntity?) {
        if (minutes <= 0) return
        viewModelScope.launch {
            val name = subject?.name ?: "Estudo Geral"
            db.studySessionDao().insert(StudySessionEntity(subject?.id, name, minutes))
            db.xpDao().insert(XpTransactionEntity(date=LocalDate.now().toString(), amount=minutes, reason="Estudo: $name"))
            history("Estudou $minutes min de $name (+$minutes XP)")
        }
    }
    fun logAction(text: String) { if (text.isNotBlank()) history("Ação: ${text.trim()}") }
    fun clearHistory() { viewModelScope.launch { db.historyDao().clear() } }
    fun pauseProtectionForSetup() {
        if (state.value.hardcoreActive) return
        viewModelScope.launch {
            val cur = db.settingsDao().getNow() ?: AppSettingsEntity()
            db.settingsDao().upsert(cur.copy(protectApps=false, protectSites=false, adultFilter=false, visualFilter=false))
            history("Proteção pausada para configuração do sistema")
        }
    }

    fun startHardcore() {
        val ch = state.value.challenge ?: return
        if (!Protection.isActive(ch) || state.value.hardcoreActive) return
        edit { it.copy(hardcore = true, hardcoreEnd = Protection.endOf(ch).toString()) }
    }
}
