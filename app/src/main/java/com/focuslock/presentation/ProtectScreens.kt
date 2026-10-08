package com.focuslock.presentation

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.data.AppSettingsEntity
import com.focuslock.services.AppBlockingService
import com.focuslock.services.FocusAdminReceiver
import com.focuslock.services.FocusVpnService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val pad = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 28.dp)
private val shape9 = RoundedCornerShape(9.dp)
private val suggestedApps = setOf(
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.instagram.android", "com.facebook.katana",
    "com.facebook.lite", "com.twitter.android", "com.google.android.youtube", "com.snapchat.android",
    "com.kwai.video", "com.reddit.frontpage", "com.pinterest", "com.discord", "org.telegram.messenger",
    "com.tumblr", "tv.twitch.android.app"
)

@Composable
private fun resumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    return tick
}

private fun isAccessibilityOn(ctx: Context): Boolean {
    val cn = ComponentName(ctx, AppBlockingService::class.java)
    val v = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return v.split(':').any { it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true) }
}

@Composable
private fun Header(kicker: String, title: String, lead: String) {
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker(kicker); H1(title); Lead(lead)
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    Text("‹  Voltar", color = C.Accent2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 6.dp))
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = C.Faint, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.Bg, checkedTrackColor = C.Accent,
                uncheckedThumbColor = C.Dim, uncheckedTrackColor = C.Surface3, uncheckedBorderColor = C.Border,
                disabledCheckedTrackColor = C.Accent.copy(alpha = 0.4f), disabledUncheckedTrackColor = C.Surface3
            )
        )
    }
}

@Composable
private fun StatusCard(title: String, body: String, tag: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.panel(14.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.weight(1f), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (tag != null) TinyChip(tag, accent = true)
        }
        Text(body, color = C.Dim, fontSize = 13.sp, lineHeight = 20.sp)
        if (action != null) LineButton(action, onAction)
    }
}

@Composable
private fun LevelChip(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(shape9)
            .background(if (selected) C.Accent.copy(alpha = 0.14f) else C.Surface)
            .border(1.dp, if (selected) C.Accent else C.Border, shape9)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (selected) C.Text else C.Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun ProtectScreen(
    s: UiState,
    onProtection: ((AppSettingsEntity) -> AppSettingsEntity) -> Unit,
    onHardcore: () -> Unit,
    onPauseForSetup: () -> Unit,
    onOpen: (String) -> Unit
) {
    val ctx = LocalContext.current
    val tick = resumeTick()
    val a11y = remember(tick) { isAccessibilityOn(ctx) }
    val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
    val admin = ComponentName(ctx, FocusAdminReceiver::class.java)
    var adminOn by remember(tick) { mutableStateOf(dpm.isAdminActive(admin)) }
    val vpnOn by FocusVpnService.running.collectAsStateWithLifecycle()
    val st = s.settings
    val locked = s.hardcoreActive
    val hasModel = remember { ctx.assets.list("")?.contains("nsfw.tflite") == true }
    val engine = if (hasModel) "modelo TensorFlow Lite" else "detector de pele (aproximado)"
    var confirm by remember { mutableStateOf(false) }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) ctx.startService(Intent(ctx, FocusVpnService::class.java))
    }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        adminOn = dpm.isAdminActive(admin)
    }

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Segurança", "Proteção", "Ative os serviços e escolha o que o FocusLock bloqueia durante o desafio.") }
        item {
            StatusCard(
                "Configuração do Android",
                "Se o FocusLock estiver bloqueando os Ajustes ou você ainda não conseguiu ativar a Acessibilidade, pause a proteção temporariamente. Depois de ativar o serviço, volte e ligue a proteção novamente.",
                tag = if (locked) "Inquebrável ativo" else null,
                action = if (!locked) "Pausar proteção e abrir Acessibilidade" else null,
                onAction = if (!locked) { { onPauseForSetup(); ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } else null
            )
        }
        item {
            StatusCard(
                "Bloqueio de aplicativos",
                if (a11y) "Serviço de acessibilidade ativo. Os aplicativos escolhidos são bloqueados durante o desafio."
                else "Ative o serviço de acessibilidade do FocusLock nos ajustes do sistema para detectar quando um aplicativo bloqueado é aberto.",
                tag = if (a11y) "Ativo" else null,
                action = if (a11y) null else "Abrir ajustes de acessibilidade",
                onAction = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            )
        }
        item {
            StatusCard(
                "Filtro de sites (VPN local)",
                if (vpnOn) "Filtro de DNS ligado. Sites bloqueados deixam de abrir em todos os navegadores e aplicativos."
                else "Liga uma VPN local que filtra apenas consultas de DNS. Não lê nem descriptografa o conteúdo das páginas.",
                tag = if (vpnOn) "Ativo" else null,
                action = if (vpnOn) (if (locked) null else "Desligar") else "Ligar filtro",
                onAction = {
                    if (vpnOn) {
                        ctx.startService(Intent(ctx, FocusVpnService::class.java).setAction(FocusVpnService.ACTION_STOP))
                    } else {
                        val i = VpnService.prepare(ctx)
                        if (i != null) vpnLauncher.launch(i) else ctx.startService(Intent(ctx, FocusVpnService::class.java))
                    }
                }
            )
        }
        item {
            Column(Modifier.panel(14.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ToggleRow("Aplicativos", "Bloquear os aplicativos escolhidos.", st.protectApps, !locked) { v -> onProtection { it.copy(protectApps = v) } }
                ToggleRow("Sites", "Bloquear os sites da sua lista.", st.protectSites, !locked) { v -> onProtection { it.copy(protectSites = v) } }
                ToggleRow("Conteúdo adulto", "Bloquear sites adultos conhecidos. A lista já vem pronta.", st.adultFilter, !locked) { v -> onProtection { it.copy(adultFilter = v) } }
                ToggleRow("Filtro visual", "Analisa a tela no aparelho e bloqueia conteúdo explícito. Requer Android 11 ou mais novo.", st.visualFilter, !locked && Build.VERSION.SDK_INT >= 30) { v -> onProtection { it.copy(visualFilter = v) } }
            }
        }
        item {
            Column(Modifier.panel(14.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sensibilidade do filtro", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Bloqueia sites a partir do nível escolhido.", color = C.Faint, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Sugestivo", 2 to "Sexual", 3 to "Explícito").forEach { (v, label) ->
                        LevelChip(label, st.sensitivity == v, !locked, Modifier.weight(1f)) { onProtection { it.copy(sensitivity = v) } }
                    }
                }
            }
        }
        item { LineButton("Aplicativos bloqueados (${s.blockedApps.size})", { onOpen("apps") }) }
        item { LineButton("Sites bloqueados (${s.domains.size})", { onOpen("domains") }) }
        item {
            StatusCard(
                "Administrador do dispositivo",
                "Opcional. Com ele ativo, desinstalar o FocusLock exige antes desativar o administrador nos ajustes do sistema.",
                tag = if (adminOn) "Ativo" else null,
                action = if (adminOn) (if (locked) null else "Desativar") else "Ativar",
                onAction = {
                    if (adminOn) {
                        dpm.removeActiveAdmin(admin)
                        adminOn = false
                    } else {
                        adminLauncher.launch(
                            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Dificulta desinstalar o FocusLock durante o modo Inquebrável.")
                        )
                    }
                }
            )
        }
        item {
            val end = if (locked) LocalDateTime.parse(st.hardcoreEnd).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) else ""
            StatusCard(
                "Modo Inquebrável",
                if (locked) "Ativo até $end. Aplicativos, sites e sensibilidade não podem ser reduzidos ou desligados, e as telas de ajustes do sistema que tratam do FocusLock são bloqueadas."
                else if (s.protectionActive) "Trava as suas escolhas até o fim do desafio atual."
                else "Crie um desafio na tela inicial para poder ativar o Hardcore.",
                tag = if (locked) "Ativo" else null,
                action = if (!locked && s.protectionActive) "Ativar modo Inquebrável" else null,
                onAction = { confirm = true }
            )
        }
        item {
            StatusCard(
                "Filtro visual",
                if (Build.VERSION.SDK_INT < 30) "Este recurso usa captura de tela pela acessibilidade e exige Android 11 ou mais novo."
                else "Motor atual: $engine. A tela é analisada só no aparelho, na hora, e a imagem nunca é salva nem enviada. Para mais precisão, coloque um modelo nsfw.tflite em app/src/main/assets e gere o APK de novo.",
                tag = if (hasModel) "Modelo TFLite" else "Aproximado"
            )
        }
        item {
            StatusCard(
                "Limites do Android",
                "Um aplicativo comum não impede de forma absoluta que o dono do aparelho o desative ou desinstale. O Modo Inquebrável dificulta bastante, mas o Android ainda pode revogar permissões ou desativar serviços pelo sistema. A VPN pode ser desligada nos ajustes do sistema, e o DNS privado do Android pode contornar o filtro."
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = C.Surface2, titleContentColor = C.Text, textContentColor = C.Dim,
            title = { Text("Ativar modo Inquebrável?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Este modo foi criado para impedir alterações de proteção pelo próprio FocusLock durante o desafio.\n\n" +
                        "Duração: até o fim do desafio atual.\n" +
                        "Aplicativos: ${s.blockedApps.size}  ·  Sites: ${s.domains.size}\n" +
                        "Sensibilidade: ${listOf("", "Sugestivo", "Sexual", "Explícito")[st.sensitivity.coerceIn(1, 3)]}\n\n" +
                        "Depois de ativado, não existe botão para desativá-lo ou pausar a proteção pelo app. Ele termina somente quando o desafio chegar ao fim."
                )
            },
            confirmButton = {
                TextButton(onClick = { onHardcore(); confirm = false }, colors = ButtonDefaults.textButtonColors(contentColor = C.Accent2)) {
                    Text("Ativar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }, colors = ButtonDefaults.textButtonColors(contentColor = C.Dim)) { Text("Cancelar") } }
        )
    }
}

@Composable
fun AppsScreen(s: UiState, onToggle: (String, String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<Pair<String, String>>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != ctx.packageName }
                .sortedWith(compareBy({ it.first !in suggestedApps }, { it.second.lowercase() }))
        }
    }
    var q by remember { mutableStateOf("") }
    val blocked = s.blockedApps.map { it.packageName }.toSet()
    val shown = apps.filter { q.isBlank() || it.second.contains(q, true) }

    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackRow(onBack) }
        item { Header("Proteção", "Aplicativos", if (s.hardcoreActive) "Modo Inquebrável ativo: aplicativos bloqueados não podem ser removidos." else "Escolha o que será bloqueado durante o desafio.") }
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Buscar") }, singleLine = true, shape = shape9, colors = fieldColors())
        }
        items(shown, key = { it.first }) { (pkg, label) ->
            val on = pkg in blocked
            Row(Modifier.panel(14.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(if (pkg in suggestedApps) "Sugerido  ·  $pkg" else pkg, color = C.Faint, fontSize = 11.sp, maxLines = 1)
                }
                if (on && s.hardcoreActive) TinyChip("Bloqueado", accent = true)
                else LineButton(if (on) "Remover" else "Bloquear", { onToggle(pkg, label) }, Modifier.width(100.dp))
            }
        }
    }
}

@Composable
fun DomainsScreen(s: UiState, onAdd: (String) -> Unit, onRemove: (String) -> Unit, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackRow(onBack) }
        item { Header("Proteção", "Sites", "Sites adultos conhecidos já são filtrados quando Conteúdo adulto está ligado. Aqui você soma os seus.") }
        item {
            Column(Modifier.panel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Ex: exemplo.com") }, singleLine = true, shape = shape9, colors = fieldColors())
                PrimaryButton("Adicionar site", { if (text.isNotBlank()) { onAdd(text); text = "" } })
            }
        }
        items(s.domains, key = { it.domain }) { d ->
            Row(Modifier.panel(14.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(d.domain, Modifier.weight(1f), color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (s.hardcoreActive) TinyChip("Bloqueado", accent = true)
                else LineButton("Remover", { onRemove(d.domain) }, Modifier.width(100.dp))
            }
        }
    }
}

@Composable
fun SettingsScreen(s: UiState, onName: (String) -> Unit, onNotifications: (Boolean) -> Unit) {
    var name by remember(s.settings.userName) { mutableStateOf(s.settings.userName) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LazyColumn(contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Ajustes", "Ajustes", "Perfil, lembretes, privacidade e informações do aplicativo.") }
        item {
            Column(Modifier.panel(14.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Perfil", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Seu nome") }, singleLine = true, shape = shape9, colors = fieldColors())
                LineButton("Salvar nome", { onName(name) })
            }
        }
        item {
            Column(Modifier.panel(14.dp).padding(18.dp)) {
                ToggleRow("Lembretes", "No máximo uma notificação por dia, perto das 20h.", s.settings.notifications) { on ->
                    onNotifications(on)
                    if (on && Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        items(
            listOf(
                "Privacidade" to "Desafios, objetivos, XP, dias, aplicativos e sites escolhidos ficam salvos só neste aparelho. Nada é enviado aos servidores do FocusLock, e imagens suas nunca são enviadas.",
                "Rede" to "Com o filtro de sites ligado, as consultas de DNS são encaminhadas ao DNS público do Google (8.8.8.8). O app só vê o nome do site consultado, nunca o conteúdo da página, e não guarda esse histórico.",
                "Permissões" to "Acessibilidade (saber qual app foi aberto, mostrar o bloqueio e, com o filtro visual ligado, analisar a tela no aparelho), VPN (filtro de DNS), notificações (lembretes) e administrador do dispositivo (opcional, dificulta desinstalar).",
                "Versão" to "FocusLock 1.0.0"
            )
        ) { (t, b) ->
            Column(Modifier.panel(14.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(b, color = C.Dim, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun OnboardingScreen(onFinish: (String, String, Int) -> Unit) {
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf("30 dias de foco") }
    var days by remember { mutableStateOf("30") }
    val last = pager.currentPage == 3

    Column(Modifier.appBackground().padding(24.dp)) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (page) {
                        0 -> { Kicker("FocusLock"); H1("Recupere o controle do seu tempo."); Lead("Períodos de foco, bloqueio de aplicativos e sites, e uma sequência para acompanhar sua disciplina.") }
                        1 -> { Kicker("Passo 1"); H1("Escolha o que você quer proteger."); Lead("Aplicativos, sites e conteúdo adulto. Você ajusta tudo depois, na aba Proteção.") }
                        2 -> {
                            Kicker("Passo 2"); H1("Defina seu desafio.")
                            OutlinedTextField(challenge, { challenge = it }, Modifier.fillMaxWidth(), label = { Text("Nome do desafio") }, singleLine = true, shape = shape9, colors = fieldColors())
                            OutlinedTextField(
                                days, { days = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Duração em dias") }, singleLine = true,
                                shape = shape9, colors = fieldColors(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                        }
                        else -> {
                            Kicker("Passo 3"); H1("Comece sua sequência.")
                            Lead("Como devemos chamar você?")
                            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Seu nome") }, singleLine = true, shape = shape9, colors = fieldColors())
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { i ->
                Box(Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 22.dp else 7.dp, 7.dp).clip(CircleShape).background(if (i == pager.currentPage) C.Accent else C.Surface3))
            }
        }
        PrimaryButton(if (last) "Vamos começar" else "Continuar", {
            if (last) onFinish(name, challenge, days.toIntOrNull() ?: 30)
            else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        })
    }
}
