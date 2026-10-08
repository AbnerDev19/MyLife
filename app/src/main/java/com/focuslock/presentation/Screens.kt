package com.focuslock.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.data.GoalEntity
import com.focuslock.domain.Achievements
import com.focuslock.domain.Gamification
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val gap = Arrangement.spacedBy(24.dp)
private val pad = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 28.dp)
private val fieldShape = RoundedCornerShape(9.dp)

@Composable
private fun Heatmap(days: Map<LocalDate, Int>, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val end = today.plusDays((7 - today.dayOfWeek.value).toLong())
    val weeks = 15
    val start = end.minusDays((weeks * 7 - 1).toLong())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (w in 0 until weeks) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    val xp = days[date]
                    val color = when {
                        date.isAfter(today) -> Color.Transparent
                        xp == null -> C.Surface3
                        xp < 300 -> C.Accent.copy(alpha = 0.35f)
                        xp < 1000 -> C.Accent.copy(alpha = 0.65f)
                        else -> C.Accent
                    }
                    val shape = RoundedCornerShape(3.dp)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(color)
                            .then(if (date == selected) Modifier.border(1.5.dp, C.Text, shape) else Modifier)
                            .clickable(enabled = !date.isAfter(today)) { onSelect(date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text("Menos", color = C.Faint, fontSize = 10.sp)
        listOf(C.Surface3, C.Accent.copy(alpha = 0.35f), C.Accent.copy(alpha = 0.65f), C.Accent).forEach {
            Box(Modifier.padding(start = 4.dp).size(10.dp).clip(RoundedCornerShape(2.dp)).background(it))
        }
        Text("Mais", color = C.Faint, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun DashboardScreen(s: UiState, onCheckIn: () -> Unit, onCreate: (String, Int) -> Unit) {
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var dialog by remember { mutableStateOf(false) }
    val hour = LocalTime.now().hour
    val greet = when { hour < 12 -> "Bom dia"; hour < 18 -> "Boa tarde"; else -> "Boa noite" }
    val ch = s.challenge

    LazyColumn(contentPadding = pad, verticalArrangement = gap) {
        item {
            Column(Modifier.hero().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Kicker(if (s.settings.userName.isBlank()) greet else "$greet, ${s.settings.userName}")
                H1("${s.streak} ${if (s.streak == 1) "dia" else "dias"} seguidos")
                if (ch == null) {
                    Lead("Defina um desafio para começar a sua sequência.")
                } else {
                    val frac = s.challengeDone.toFloat() / ch.totalDays
                    Lead(ch.name)
                    ProgressBar(frac)
                    Text(
                        "${s.challengeDone} / ${ch.totalDays} dias  ·  ${(frac * 100).toInt()}%  ·  faltam ${ch.totalDays - s.challengeDone}",
                        color = C.Faint, fontSize = 12.sp
                    )
                }
                StatRow(listOf(s.xp.toString() to "XP", Gamification.level(s.xp).toString() to "Nível", "${s.bestStreak} d" to "Melhor sequência"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Proteção", color = C.Faint, fontSize = 12.sp)
                    TinyChip("${s.blockedApps.size} apps", s.settings.protectApps)
                    TinyChip("Sites", s.settings.protectSites)
                    TinyChip("Adulto", s.settings.adultFilter)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(if (s.doneToday) "Dia de hoje concluído" else "Concluir o dia de hoje", onCheckIn, enabled = !s.doneToday)
                    if (ch == null) LineButton("Criar desafio", { dialog = true })
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Calendário de disciplina")
                Column(Modifier.panel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Heatmap(s.days, selected) { selected = it }
                    Legend()
                    val sel = selected
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (sel == null) TinyChip("Toque em um dia")
                        else {
                            TinyChip(sel.format(fmt))
                            val xp = s.days[sel]
                            if (xp != null) TinyChip("+$xp XP", accent = true) else TinyChip("Sem check-in")
                            val b = s.blocks[sel] ?: 0
                            if (b > 0) TinyChip("$b bloqueios")
                        }
                    }
                }
            }
        }
    }

    if (dialog) {
        var name by remember { mutableStateOf("30 dias de foco") }
        var days by remember { mutableStateOf("30") }
        AlertDialog(
            onDismissRequest = { dialog = false },
            containerColor = C.Surface2, titleContentColor = C.Text, textContentColor = C.Dim,
            title = { Text("Novo desafio", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, shape = fieldShape, colors = fieldColors())
                    OutlinedTextField(
                        days, { days = it.filter(Char::isDigit) }, label = { Text("Duração em dias") }, singleLine = true,
                        shape = fieldShape, colors = fieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onCreate(name.ifBlank { "Meu desafio" }, (days.toIntOrNull() ?: 30).coerceAtLeast(1)); dialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = C.Accent2)
                ) { Text("Criar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = C.Dim)) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun GoalsScreen(s: UiState, onAdd: (String) -> Unit, onComplete: (GoalEntity) -> Unit) {
    var title by remember { mutableStateOf("") }
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker("Metas")
                H1("Objetivos")
                Lead("Cada objetivo concluído soma XP ao seu nível.")
            }
        }
        item {
            Column(Modifier.panel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Novo objetivo") },
                    singleLine = true, shape = fieldShape, colors = fieldColors()
                )
                PrimaryButton("Adicionar", { if (title.isNotBlank()) { onAdd(title.trim()); title = "" } })
            }
        }
        items(s.goals, key = { it.id }) { g ->
            Row(Modifier.panel(14.dp).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row { TinyChip("+${g.xp} XP") }
                }
                if (g.done) TinyChip("Concluído", accent = true)
                else LineButton("Concluir", { onComplete(g) }, Modifier.width(96.dp))
            }
        }
    }
}

@Composable
fun ProgressScreen(s: UiState) {
    val next = Gamification.nextLevelXp(s.xp)
    val letters = listOf("S", "T", "Q", "Q", "S", "S", "D")
    LazyColumn(contentPadding = pad, verticalArrangement = gap) {
        item {
            Column(Modifier.hero().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Kicker("Progresso")
                H1("Nível ${Gamification.level(s.xp)}")
                ProgressBar(Gamification.levelProgress(s.xp))
                Text(
                    if (next == null) "${s.xp} XP  ·  nível máximo" else "${s.xp} / $next XP  ·  faltam ${next - s.xp}",
                    color = C.Faint, fontSize = 12.sp
                )
                StatRow(listOf(s.days.size.toString() to "Dias", "${s.streak} d" to "Sequência", s.goals.count { it.done }.toString() to "Objetivos"))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Últimos 7 dias")
                Row(Modifier.panel().padding(18.dp).height(84.dp)) {
                    for (i in 6 downTo 0) {
                        val date = LocalDate.now().minusDays(i.toLong())
                        val done = date in s.days
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(Modifier.width(22.dp).height(if (done) 52.dp else 6.dp).clip(RoundedCornerShape(4.dp)).background(if (done) C.Accent else C.Surface3))
                            Spacer(Modifier.height(6.dp))
                            Text(letters[date.dayOfWeek.value - 1], color = C.Faint, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Conquistas")
                Column(Modifier.panel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Achievements.all.forEach { a ->
                        val got = a.id in s.achievements
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(a.title, color = if (got) C.Text else C.Faint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(a.description, color = C.Faint, fontSize = 12.sp)
                            }
                            TinyChip(if (got) "Conquistada" else "Bloqueada", accent = got)
                        }
                    }
                }
            }
        }
    }
}

data class InfoItem(val title: String, val body: String, val tag: String? = null)

@Composable
fun InfoScreen(kicker: String, title: String, lead: String, items: List<InfoItem>) {
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker(kicker)
                H1(title)
                Lead(lead)
            }
        }
        items(items) {
            Column(Modifier.panel(14.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(it.title, Modifier.weight(1f), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (it.tag != null) TinyChip(it.tag, accent = true)
                }
                Text(it.body, color = C.Dim, fontSize = 13.sp, lineHeight = 21.sp)
            }
        }
    }
}
