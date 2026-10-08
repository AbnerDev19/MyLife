package com.focuslock.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.data.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProductivityScreen(s: UiState, vm: MainViewModel) {
    var dialog by remember { mutableStateOf<String?>(null) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var selectedSubject by remember { mutableStateOf<SubjectEntity?>(null) }

    LaunchedEffect(timerRunning) {
        while (timerRunning) { delay(1000); timerSeconds++ }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker("Rotina")
                H1("Seu dia, do seu jeito")
                Lead("As funções do seu primeiro projeto agora vivem dentro do FocusLock: missões, hábitos, prazos, estudo, XP, atributos e histórico.")
                StatRow(listOf(s.xp.toString() to "XP", Gamification.level(s.xp).toString() to "Nível", s.goals.count { it.done }.toString() to "Metas"))
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LineButton("Nova missão", { dialog="goal" }, Modifier.weight(1f)); LineButton("Novo hábito", { dialog="habit" }, Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LineButton("Nova atividade", { dialog="activity" }, Modifier.weight(1f)); LineButton("Novo atributo", { dialog="attribute" }, Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { LineButton("Nova matéria", { dialog="subject" }, Modifier.weight(1f)); LineButton("Tempo manual", { dialog="study" }, Modifier.weight(1f)) } }
        item { LineButton("Registrar ação", { dialog="log" }) }

        item { SectionTitle("Missões") }
        items(s.goals, key={it.id}) { g ->
            Row(Modifier.panel(14.dp).padding(16.dp), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { Text(g.title, color=C.Text, fontWeight=FontWeight.SemiBold); TinyChip("+${g.xp} XP") }
                if (g.done) TinyChip("Concluída", accent=true) else LineButton("Concluir", { vm.completeGoal(g) }, Modifier.width(92.dp))
            }
        }

        item { SectionTitle("Hábitos") }
        items(s.habits, key={it.id}) { h ->
            val done = h.completedDate == LocalDate.now().toString()
            Row(Modifier.panel(14.dp).padding(16.dp), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { Text(h.name, color=C.Text, fontWeight=FontWeight.SemiBold); Text("🔥 ${h.streak} dia(s)", color=C.Faint, fontSize=12.sp) }
                LineButton(if(done) "Feito" else "+10 XP", { if(!done) vm.toggleHabit(h) }, Modifier.width(92.dp))
            }
        }

        item { SectionTitle("Atividades com prazo") }
        items(s.activities, key={it.id}) { a ->
            val overdue = !a.completed && !a.failed && a.dueAt < System.currentTimeMillis()
            if (overdue && !a.failed) { /* visual only; failure is intentionally not automatic here */ }
            Row(Modifier.panel(14.dp).padding(16.dp), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) { Text(a.name, color=C.Text, fontWeight=FontWeight.SemiBold); Text("+${a.xp} XP · vence em ${DateTimeFormatter.ofPattern("dd/MM HH:mm").format(java.time.Instant.ofEpochMilli(a.dueAt).atZone(java.time.ZoneId.systemDefault()))}", color=if(overdue) C.Accent2 else C.Faint, fontSize=12.sp) }
                if(a.completed) TinyChip("Concluída", accent=true) else if(a.failed) TinyChip("Falhou") else LineButton("Concluir", { vm.completeActivity(a) }, Modifier.width(92.dp))
            }
        }

        item { SectionTitle("Sala de estudos") }
        item {
            Column(Modifier.panel(14.dp).padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(formatTimer(timerSeconds), color=C.Text, fontSize=40.sp, fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if (!timerRunning) LineButton("Iniciar", { timerRunning=true }, Modifier.weight(1f)) else LineButton("Pausar", { timerRunning=false }, Modifier.weight(1f))
                    LineButton("Finalizar + XP", { timerRunning=false; val min=timerSeconds/60; if(min>0) vm.addStudy(min, selectedSubject); timerSeconds=0 }, Modifier.weight(1f))
                }
                Text("Matéria atual: ${selectedSubject?.name ?: "Estudo Geral"}", color=C.Faint, fontSize=12.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { s.subjects.take(4).forEach { sub -> AssistChip(onClick={selectedSubject=sub}, label={Text(sub.name)}) } }
            }
        }
        item { SectionTitle("Atributos") }
        items(s.attributes, key={it.id}) { a -> Row(Modifier.panel(14.dp).padding(14.dp), horizontalArrangement=Arrangement.SpaceBetween) { Text(a.name, color=C.Text); Text("Lvl ${a.level} · ${a.xp} XP", color=C.Faint) } }
        item { SectionTitle("Histórico recente") }
        items(s.history.take(15), key={it.id}) { h -> Text("${java.time.Instant.ofEpochMilli(h.timestamp).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}  ${h.text}", color=C.Dim, fontSize=12.sp) }
        item { LineButton("Limpar histórico", vm::clearHistory) }
    }

    when(dialog) {
        "goal" -> SimpleTextDialog("Nova missão", "Nome", "50", { name,xp -> vm.addRoutineGoal(name, xp); dialog=null }, onCancel={dialog=null})
        "habit" -> SimpleTextDialog("Novo hábito", "Nome", "", { name,_ -> vm.addHabit(name, null); dialog=null }, false, onCancel={dialog=null})
        "attribute" -> SimpleTextDialog("Novo atributo", "Nome", "", { name,_ -> vm.addAttribute(name); dialog=null }, false, onCancel={dialog=null})
        "subject" -> SimpleTextDialog("Nova matéria", "Nome", "", { name,_ -> vm.addSubject(name); dialog=null }, false, onCancel={dialog=null})
        "activity" -> SimpleTextDialog("Nova atividade", "Nome", "60", { name,xp -> vm.addActivity(name, System.currentTimeMillis()+60*60_000L, xp.coerceAtLeast(1), null); dialog=null }, onCancel={dialog=null})
        "study" -> SimpleTextDialog("Tempo manual", "Minutos", "30", { _,mins -> vm.addStudy(mins, selectedSubject); dialog=null }, false, numeric=true, onCancel={dialog=null})
        "log" -> SimpleTextDialog("Registrar ação", "O que você fez?", "", { name,_ -> vm.logAction(name); dialog=null }, false, onCancel={dialog=null})
    }
}

private fun formatTimer(sec:Int):String = String.format("%02d:%02d:%02d", sec/3600, (sec%3600)/60, sec%60)

@Composable
private fun SimpleTextDialog(title:String, label:String, secondDefault:String, onSave:(String,Int)->Unit, hasNumber:Boolean=true, numeric:Boolean=false, onCancel:()->Unit) {
    var text by remember { mutableStateOf("") }
    var number by remember { mutableStateOf(secondDefault) }
    AlertDialog(onDismissRequest=onCancel, containerColor=C.Surface2, title={Text(title,color=C.Text)},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(text,{text=it},label={Text(label)},singleLine=true); if(hasNumber) OutlinedTextField(number,{number=it.filter(Char::isDigit)},label={Text(if(numeric)"Minutos" else "Valor")},singleLine=true)}},
        confirmButton={TextButton(onClick={ if(text.isNotBlank()) onSave(text, number.toIntOrNull()?:0) }, colors=ButtonDefaults.textButtonColors(contentColor=C.Accent2)){Text("Salvar")} },
        dismissButton={TextButton(onClick=onCancel){Text("Fechar")}})
}
