package com.focuslock.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

fun Modifier.panel(radius: Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.fillMaxWidth().clip(shape).background(C.Surface).border(1.dp, C.Border, shape)
}

fun Modifier.hero(): Modifier {
    val shape = RoundedCornerShape(20.dp)
    return this.fillMaxWidth().clip(shape).background(C.Surface)
        .background(Brush.linearGradient(0f to C.Accent.copy(alpha = 0.09f), 0.4f to Color.Transparent, 1f to Color.Transparent))
        .border(1.dp, C.Border, shape)
}

@Composable
fun Kicker(text: String) = Text(text, color = C.Accent2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun H1(text: String) = Text(text, color = C.Text, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.025).em)

@Composable
fun SectionTitle(text: String) = Text(text, color = C.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em)

@Composable
fun Lead(text: String) = Text(text, color = C.Dim, fontSize = 15.sp, lineHeight = 24.sp)

@Composable
fun TinyChip(text: String, accent: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Text(
        text, fontSize = 11.sp, maxLines = 1, color = if (accent) C.Text else C.Dim,
        modifier = Modifier.clip(shape)
            .background(if (accent) C.Accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (accent) C.Accent.copy(alpha = 0.55f) else C.Border, shape)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(9.dp))
            .background(if (enabled) C.Text else C.Surface3)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) C.Bg else C.Faint, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun LineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.height(48.dp).clip(shape).border(1.dp, C.BorderStrong, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun ProgressBar(progress: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.Surface3)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(C.Accent))
    }
}

/** Linha de números separados por divisórias verticais, como o stat-row do portfólio. */
@Composable
fun StatRow(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        items.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(C.Border))
            Column(Modifier.weight(1f).padding(start = if (i == 0) 0.dp else 16.dp)) {
                Text(value, color = C.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(label, color = C.Faint, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Accent, unfocusedBorderColor = C.Border,
    focusedTextColor = C.Text, unfocusedTextColor = C.Text, cursorColor = C.Accent,
    focusedLabelColor = C.Accent2, unfocusedLabelColor = C.Faint,
    focusedContainerColor = Color(0xFF0C0F14), unfocusedContainerColor = Color(0xFF0C0F14)
)

fun Modifier.appBackground(): Modifier = this.fillMaxSize().drawBehind {
    drawRect(C.Bg)
    drawRect(Brush.radialGradient(listOf(C.Accent.copy(alpha = 0.10f), Color.Transparent), Offset(size.width * 0.15f, 0f), size.width))
    drawRect(Brush.radialGradient(listOf(Color(0xFF6496FF).copy(alpha = 0.06f), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.2f), size.width * 0.9f))
}
