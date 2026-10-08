package com.focuslock.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Início", Icons.Filled.Home),
    Tab("goals", "Objetivos", Icons.Filled.CheckCircle),
    Tab("routine", "Rotina", Icons.Filled.Star),
    Tab("progress", "Progresso", Icons.Filled.Star),
    Tab("protect", "Proteção", Icons.Filled.Lock),
    Tab("settings", "Ajustes", Icons.Filled.Settings)
)

@Composable
fun FocusLockApp(vm: MainViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    when {
        !s.loaded -> Box(Modifier.appBackground())
        !s.settings.onboarded -> OnboardingScreen(vm::finishOnboarding)
        else -> MainShell(vm, s)
    }
}

@Composable
private fun MainShell(vm: MainViewModel, s: UiState) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val tabRoute = if (current == "apps" || current == "domains") "protect" else current

    Column(Modifier.appBackground()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                Text("FocusLock", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em)
                Text(".", color = C.Accent2, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (s.protectionActive) C.Green else C.Faint))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (s.hardcoreActive) "Hardcore" else if (s.protectionActive) "Proteção ativa" else "Sem desafio",
                    color = C.Dim, fontSize = 12.sp
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))

        NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
            composable("home") { DashboardScreen(s, vm::checkIn, vm::createChallenge) }
            composable("goals") { GoalsScreen(s, vm::addGoal, vm::completeGoal) }
            composable("routine") { ProductivityScreen(s, vm) }
            composable("progress") { ProgressScreen(s) }
            composable("protect") { ProtectScreen(s, vm::setProtection, vm::startHardcore, vm::pauseProtectionForSetup) { nav.navigate(it) } }
            composable("apps") { AppsScreen(s, vm::toggleApp) { nav.popBackStack() } }
            composable("domains") { DomainsScreen(s, vm::addDomain, vm::removeDomain) { nav.popBackStack() } }
            composable("settings") { SettingsScreen(s, vm::setName, vm::setNotifications) }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
        Row(Modifier.fillMaxWidth().background(C.Bg.copy(alpha = 0.88f)).height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val on = tabRoute == t.route
                Column(
                    Modifier.weight(1f).padding(horizontal = 3.dp).clip(RoundedCornerShape(9.dp))
                        .background(if (on) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                        .clickable {
                            nav.navigate(t.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = if (on) C.Text else C.Faint, modifier = Modifier.size(20.dp))
                    Text(t.label, color = if (on) C.Text else C.Faint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
