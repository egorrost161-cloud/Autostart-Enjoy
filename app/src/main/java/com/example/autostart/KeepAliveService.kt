package com.example.autostart

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isStarted = false

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
        val targetPackages = prefs.getStringSet("target_packages", emptySet()) ?: emptySet()

        if (targetPackages.isEmpty()) {
            LogWriter.log("Пакеты не выбраны — выход")
            stopSelf()
            return START_NOT_STICKY
        }

        if (isStarted) {
            LogWriter.log("Сервис уже работает")
            return START_STICKY
        }

        isStarted = true
        LogWriter.log("Сервис запущен, пакетов: ${targetPackages.size}")

        for (pkg in targetPackages) {
            val delaySec = prefs.getLong("delay_$pkg", 15L)
            val delayMs = delaySec * 1000
            handler.postDelayed({
                launchApp(pkg, delaySec)
            }, delayMs)
        }

        return START_STICKY
    }

    private fun launchApp(pkg: String, delaySec: Long) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                LogWriter.log("+ Запущен $pkg через ${delaySec}с")
            } else {
                LogWriter.log("- Не найдена точка входа для $pkg")
            }
        } catch (e: Exception) {
            LogWriter.log("- ОШИБКА запуска $pkg: ${e.message}")
        }
    }

    private fun buildNotification(): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, "autostart_channel")
            .setContentTitle("AutoStart Pro активен")
            .setContentText("Следит за запуском приложений")
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
        isStarted = false
        LogWriter.log("=== Сервис остановлен ===")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
