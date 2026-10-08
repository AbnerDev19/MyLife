package com.focuslock.services

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.focuslock.data.AppDatabase
import com.focuslock.domain.Protection
import com.focuslock.presentation.C
import com.focuslock.presentation.FocusLockTheme
import com.focuslock.presentation.Kicker
import com.focuslock.presentation.Lead
import com.focuslock.presentation.PrimaryButton
import com.focuslock.presentation.panel
import kotlinx.coroutines.delay

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg").orEmpty()
        val visual = intent.getStringExtra("reason") == "visual"
        val label = if (visual) "Conteúdo explícito detectado" else try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent { FocusLockTheme { BlockScreen(if (visual) "Conteúdo bloqueado" else "Aplicativo bloqueado", label) { goHome() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

@Composable
private fun BlockScreen(title: String, app: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val remaining by produceState("") {
        val dao = AppDatabase.get(ctx).challengeDao()
        while (true) {
            val ch = dao.activeNow()
            value = if (ch != null) Protection.remainingText(ch) else ""
            delay(30_000)
        }
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        1f, 1.28f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale"
    )
    Box(
        Modifier.fillMaxSize().background(C.Bg).drawBehind {
            drawRect(Brush.radialGradient(listOf(C.Accent.copy(alpha = 0.16f), Color.Transparent), Offset(size.width / 2, size.height * 0.3f), size.width))
        },
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(110.dp).scale(scale).clip(CircleShape).background(C.Accent.copy(alpha = 0.14f)))
                Box(Modifier.size(54.dp).clip(CircleShape).background(C.Accent))
            }
            Kicker("FOCUSLOCK")
            Text("Respira.", color = C.Text, fontSize = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em)
            Lead("Você escolheu proteger seu tempo.")
            Column(Modifier.panel().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = C.Faint, fontSize = 12.sp)
                Text(app, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (remaining.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Seu desafio termina em", color = C.Faint, fontSize = 12.sp)
                    Text(remaining, color = C.Accent2, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start)
                }
            }
            PrimaryButton("VOLTAR", onBack)
        }
    }
}
