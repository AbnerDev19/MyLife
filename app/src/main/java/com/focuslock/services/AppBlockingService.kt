package com.focuslock.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.focuslock.data.AppDatabase
import com.focuslock.data.AppSettingsEntity
import com.focuslock.data.ChallengeEntity
import com.focuslock.data.record
import com.focuslock.domain.ContentLevel
import com.focuslock.domain.DomainClassifier
import com.focuslock.domain.Protection
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppBlockingService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()
    private val screenshotInFlight = AtomicBoolean(false)
    private val classifier by lazy { ImageClassifierFactory.create(this) }

    @Volatile private var blocked: Set<String> = emptySet()
    @Volatile private var challenge: ChallengeEntity? = null
    @Volatile private var settings: AppSettingsEntity = AppSettingsEntity()
    @Volatile private var foregroundPackage = ""
    private var homePackage = ""
    private var lastBlockedPackage = ""
    private var lastBlockedAt = 0L
    private var visualCooldownUntil = 0L
    private var visualCandidatePackage = ""
    private var visualCandidateHits = 0

    private val poller = object : Runnable {
        override fun run() {
            updateForegroundFromWindow()
            scanVisual()
            handler.postDelayed(this, 700)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            notificationTimeout = 50
        }
        homePackage = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName.orEmpty()

        val db = AppDatabase.get(this)
        scope.launch { db.blockedAppDao().all().collect { blocked = it.map { app -> app.packageName }.toSet() } }
        scope.launch { db.challengeDao().active().collect { challenge = it } }
        scope.launch { db.settingsDao().observe().collect { settings = it ?: AppSettingsEntity() } }
        handler.post(poller)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        updateForeground(pkg)
    }

    private fun updateForegroundFromWindow() {
        val pkg = try { rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
        if (!pkg.isNullOrBlank()) updateForeground(pkg)
    }

    private fun updateForeground(pkg: String) {
        if (pkg == packageName) return
        foregroundPackage = pkg
        if (!Protection.isActive(challenge)) return
        if (!settings.protectApps || pkg !in blocked) return

        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPackage && now - lastBlockedAt < 1200) return
        lastBlockedPackage = pkg
        lastBlockedAt = now
        blockApp(pkg)
    }

    private fun blockApp(pkg: String) {
        scope.launch { AppDatabase.get(this@AppBlockingService).blockLogDao().record() }
        // Tiramos o app bloqueado da frente antes de abrir a tela do FocusLock.
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("pkg", pkg)
            )
        }, 120)
    }

    private fun scanVisual() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!settings.visualFilter || !Protection.isActive(challenge)) return
        if (!getSystemService(PowerManager::class.java).isInteractive) return
        val fg = foregroundPackage
        if (fg.isBlank() || fg == packageName || fg == homePackage) return
        if (SystemClock.elapsedRealtime() < visualCooldownUntil) return
        if (!screenshotInFlight.compareAndSet(false, true)) return

        takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val bitmap = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        result.hardwareBuffer.close()
                        if (bitmap != null) {
                            val level = try { classifier.classify(bitmap) } catch (_: Throwable) { ContentLevel.SAFE }
                            bitmap.recycle()

                            // Imagens explícitas só são bloqueadas com alta confiança.
                            // Conteúdo "sexy/sugestivo" não dispara bloqueio visual.
                            val candidate = level == ContentLevel.EXPLICIT
                            if (candidate && fg == foregroundPackage) {
                                if (visualCandidatePackage == fg) visualCandidateHits++ else {
                                    visualCandidatePackage = fg
                                    visualCandidateHits = 1
                                }
                                // Exige duas análises consecutivas antes de bloquear,
                                // reduzindo bastante falsos positivos de uma única captura.
                                if (visualCandidateHits >= 2) handler.post { blockVisual() }
                            } else {
                                visualCandidatePackage = ""
                                visualCandidateHits = 0
                            }
                        }
                    } finally {
                        screenshotInFlight.set(false)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    screenshotInFlight.set(false)
                }
            })
    }

    private fun blockVisual() {
        visualCandidatePackage = ""
        visualCandidateHits = 0
        visualCooldownUntil = SystemClock.elapsedRealtime() + 10000
        scope.launch { AppDatabase.get(this@AppBlockingService).blockLogDao().record() }
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("reason", "visual")
            )
        }, 150)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        screenshotExecutor.shutdownNow()
        scope.cancel()
        super.onDestroy()
    }
}
