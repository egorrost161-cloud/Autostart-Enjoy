package com.example.autostart

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        LogWriter.init(this)
        createNotificationChannel()
        LogWriter.log("=== KeepAliveService onCreate ===")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(101, notification)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val forceCheck = intent?.getBooleanExtra("FORCE_CHECK", false) ?: false

        if (forceCheck) {
            LogWriter.log("FORCE_CHECK — проверка только лаунчера")
            checkAndLaunchMonitorPackage(prefs, force = true)
            return START_STICKY
        }

        val targetSet = prefs.getStringSet("target_packages", emptySet()) ?: emptySet()

        if (targetSet.isEmpty()) {
            LogWriter.log("Пакеты не выбраны — выход")
            stopSelf()
            return START_NOT_STICKY
        }

        handler.removeCallbacksAndMessages(null)

        LogWriter.log("Сервис запущен, пакетов: ${targetSet.size}")
        for (pkg in targetSet) {
            val delaySec = prefs.getLong("delay_$pkg", 15L).coerceAtLeast(0L)
            val delayMs = delaySec * 1000
            LogWriter.log("Запланирован $pkg через ${delaySec}с от старта")
            handler.postDelayed({
                launchApp(pkg, delaySec, prefs)
            }, delayMs)
        }

        startMonitor(prefs)
        return START_STICKY
    }

    private fun launchApp(pkg: String, delaySec: Long, prefs: SharedPreferences) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                LogWriter.log("+ Запущен $pkg (задержка ${delaySec}с)")

                // ============ АВТОПЛЕЙ ДЛЯ ЯНДЕКС.МУЗЫКИ ============
                if (pkg == "ru.yandex.music") {
                    val autoplayEnabled = prefs.getBoolean("autoplay_yandex", false)
                    LogWriter.log("Яндекс.Музыка: автоплей=$autoplayEnabled")
                    if (autoplayEnabled) {
                        // Первая попытка через 3 секунды
                        handler.postDelayed({
                            tryAllAutoplayMethods()
                        }, 3000L)
                        // Повторная попытка через 6 секунд
                        handler.postDelayed({
                            LogWriter.log("=== Повторная попытка автоплея ===")
                            tryAllAutoplayMethods()
                        }, 6000L)
                    }
                }
                // =================================================

                val bgMode = prefs.getBoolean("bg_$pkg", false)
                if (bgMode) {
                    val goHomeSec = prefs.getFloat("bg_go_home_delay_sec", 1.5f)
                        .coerceIn(0.5f, 10.0f)
                    val goHomeMs = (goHomeSec * 1000).toLong()
                    handler.postDelayed({
                        goHome()
                        LogWriter.log("~ $pkg свёрнут в фон (пауза ${goHomeSec}с)")
                    }, goHomeMs)
                }
            } else {
                LogWriter.log("- Не найдена точка входа для $pkg")
            }
        } catch (e: Exception) {
            LogWriter.log("- ОШИБКА запуска $pkg: ${e.message}")
        }
    }

    // ============ АВТОПЛЕЙ: 3 СПОСОБА ============

    private fun tryAllAutoplayMethods() {
        LogWriter.log("--- Автоплей: пробуем все 3 способа ---")
        method1Keyevent()
        // Между способами — небольшая пауза
        handler.postDelayed({ method2Broadcast() }, 500L)
        handler.postDelayed({ method3MediaKey() }, 1000L)
    }

    /** Способ 1: shell-команда input keyevent 85 (KEYCODE_MEDIA_PLAY_PAUSE) */
    private fun method1Keyevent() {
        try {
            Runtime.getRuntime().exec(arrayOf("input", "keyevent", "85"))
            LogWriter.log("  [1] input keyevent 85 — отправлено")
        } catch (e: Exception) {
            LogWriter.log("  [1] input keyevent — ошибка: ${e.message}")
        }
    }

    /** Способ 2: broadcast в Яндекс.Музыку с командой PLAY */
    private fun method2Broadcast() {
        try {
            val intent = Intent("com.yandex.music.action.PLAY")
            intent.setPackage("ru.yandex.music")
            sendBroadcast(intent)
            LogWriter.log("  [2] broadcast com.yandex.music.action.PLAY — отправлено")
        } catch (e: Exception) {
            LogWriter.log("  [2] broadcast — ошибка: ${e.message}")
        }

        // Дополнительно — стандартный MEDIA_BUTTON broadcast
        try {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            intent.putExtra(
                Intent.EXTRA_KEY_EVENT,
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            )
            sendBroadcast(intent)
            LogWriter.log("  [2b] ACTION_MEDIA_BUTTON broadcast — отправлено")
        } catch (e: Exception) {
            LogWriter.log("  [2b] MEDIA_BUTTON — ошибка: ${e.message}")
        }
    }

    /** Способ 3: AudioManager.dispatchMediaKeyEvent */
    private fun method3MediaKey() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val eventTime = android.os.SystemClock.uptimeMillis()

            val downEvent = KeyEvent(
                eventTime, eventTime,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_PLAY, 0
            )
            val upEvent = KeyEvent(
                eventTime, eventTime,
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_MEDIA_PLAY, 0
            )

            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            LogWriter.log("  [3] dispatchMediaKeyEvent PLAY — отправлено")
        } catch (e: Exception) {
            LogWriter.log("  [3] dispatchMediaKeyEvent — ошибка: ${e.message}")
        }
    }

    private fun goHome() {
        try {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(home)
        } catch (e: Exception) {
            LogWriter.log("- Ошибка возврата домой: ${e.message}")
        }
    }

    // ============ МОНИТОР ============

    private fun startMonitor(prefs: SharedPreferences) {
        stopMonitor()

        val enabled = prefs.getBoolean("monitor_enabled", false)
        if (!enabled) {
            LogWriter.log("Монитор выключен")
            return
        }

        val monitorPkg = prefs.getString("monitor_package", "") ?: ""
        if (monitorPkg.isEmpty()) {
            LogWriter.log("Монитор: пакет не задан — не запускаем")
            return
        }

        val intervalSec = prefs.getLong("monitor_interval_sec", 30L).coerceIn(5L, 300L)
        val intervalMs = intervalSec * 1000

        LogWriter.log("Монитор запущен: пакет=$monitorPkg, интервал=${intervalSec}с")

        monitorRunnable = object : Runnable {
            override fun run() {
                val stillEnabled = prefs.getBoolean("monitor_enabled", false)
                if (!stillEnabled) {
                    LogWriter.log("Монитор отключён — выход")
                    monitorRunnable = null
                    return
                }
                checkAndLaunchMonitorPackage(prefs, force = false)
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.postDelayed(monitorRunnable!!, intervalMs)
    }

    private fun stopMonitor() {
        monitorRunnable?.let { handler.removeCallbacks(it) }
        monitorRunnable = null
    }

    private fun checkAndLaunchMonitorPackage(prefs: SharedPreferences, force: Boolean) {
        val monitorPkg = prefs.getString("monitor_package", "") ?: ""
        if (monitorPkg.isEmpty()) {
            LogWriter.log("Монитор: пакет не задан")
            return
        }

        val idleMinutes = prefs.getLong("monitor_idle_minutes", 30L).coerceIn(1L, 240L)
        val idleMs = idleMinutes * 60 * 1000

        val lastUsed = getLastTimeUsed(monitorPkg)
        val now = System.currentTimeMillis()
        val idleSinceMs = if (lastUsed > 0) (now - lastUsed) else Long.MAX_VALUE
        val idleMinutesActual = idleSinceMs / 60000

        val isInForeground = isPackageInForeground(monitorPkg)

        if (isInForeground) {
            LogWriter.log("Монитор: $monitorPkg уже на переднем плане — ок")
            return
        }

        if (force) {
            LogWriter.log("Монитор(FORCE): лаунчер неактивен ${idleMinutesActual}мин — запускаем")
            launchMonitorPackage(monitorPkg)
            return
        }

        if (idleSinceMs >= idleMs) {
            LogWriter.log("Монитор: лаунчер неактивен ${idleMinutesActual}мин (порог ${idleMinutes}мин) — запускаем")
            launchMonitorPackage(monitorPkg)
        } else {
            LogWriter.log("Монитор: лаунчер неактивен ${idleMinutesActual}мин — ещё рано")
        }
    }

    private fun getLastTimeUsed(pkg: String): Long {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 24 * 60 * 60 * 1000,
                now
            )
            if (stats == null) return 0
            for (s in stats) {
                if (s.packageName == pkg) {
                    return s.lastTimeUsed
                }
            }
        } catch (e: Exception) {
            LogWriter.log("getLastTimeUsed error: ${e.message}")
        }
        return 0
    }

    private fun isPackageInForeground(pkg: String): Boolean {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryEvents(now - 60 * 1000, now)
            var lastForegroundPkg: String? = null
            val event = android.app.usage.UsageEvents.Event()
            while (stats.hasNextEvent()) {
                stats.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForegroundPkg = event.packageName
                }
            }
            return lastForegroundPkg == pkg
        } catch (e: Exception) {
            return false
        }
    }

    private fun launchMonitorPackage(pkg: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                LogWriter.log("+ Монитор запустил $pkg")
            } else {
                LogWriter.log("- Монитор: пакет $pkg не найден")
            }
        } catch (e: Exception) {
            LogWriter.log("- Монитор: ошибка запуска $pkg: ${e.message}")
        }
    }

    // ============ УВЕДОМЛЕНИЕ ============

    private fun buildNotification(): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "autostart_channel")
            .setContentTitle("AutoStart Pro активен")
            .setContentText("Автозапуск и монитор лаунчера")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "autostart_channel",
                "AutoStart Pro",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Канал для сервиса автозапуска"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        monitorRunnable = null
        LogWriter.log("=== Сервис остановлен ===")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
