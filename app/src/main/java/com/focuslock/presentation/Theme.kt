package com.focuslock.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Bg = Color(0xFF08090C)
    val Surface = Color(0xFF101218)
    val Surface2 = Color(0xFF151923)
    val Surface3 = Color(0xFF1B2130)
    val Border = Color(0xFF252B38)
    val BorderStrong = Color(0xFF394154)
    val Text = Color(0xFFF4F6FB)
    val Dim = Color(0xFFA6ADBD)
    val Faint = Color(0xFF6F7788)
    val Accent = Color(0xFF8B8CF0)
    val Accent2 = Color(0xFFA7A8FF)
    val Green = Color(0xFF5FD98A)
}

private val Scheme = darkColorScheme(
    primary = C.Accent, onPrimary = C.Bg, secondary = C.Green,
    background = C.Bg, onBackground = C.Text, surface = C.Surface, onSurface = C.Text,
    surfaceVariant = C.Surface3, onSurfaceVariant = C.Dim, outline = C.Border
)

@Composable
fun FocusLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
