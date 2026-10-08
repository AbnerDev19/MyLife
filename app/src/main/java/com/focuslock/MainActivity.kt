package com.focuslock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.focuslock.presentation.FocusLockApp
import com.focuslock.presentation.FocusLockTheme
import com.focuslock.services.ReminderWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderWorker.schedule(this)
        setContent { FocusLockTheme { FocusLockApp() } }
    }
}
