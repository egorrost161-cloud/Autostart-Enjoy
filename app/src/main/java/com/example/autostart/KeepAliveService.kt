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
        val targetSet = prefs.getStringSet("target_packages", emptySet()) ?: emptySet()

        if (targetSet.isEmpty()) {
            LogWriter.log("Пакеты не выбраны — выход")
            stopSelf()
            return START_NOT_STICKY
        }

        // Отменяем старые задачи, чтобы не было дублей
        handler.removeCallbacksAndMessages(null)

        // Проверяем FORCE_CHECK — пришёл ли запрос на немедленный запуск
        val forceCheck = intent?.getBooleanExtra("FORCE_CHECK", false) ?: false

        if (forceCheck) {
            LogWriter.log("FORCE_CHECK — немедленный запуск ${targetSet.size} приложений")
            for (pkg in targetSet) {
                launchApp(pkg, 0L)
            }
            return START_STICKY
        }

        // Обычный режим — планируем запуски по индивидуальным задержкам от старта
        LogWriter.log("Сервис запущен, пакетов: ${targetSet.size}")
        for (pkg in targetSet) {
            val delaySec = prefs.getLong("delay_$pkg", 15L).coerceAtLeast(1L)
            val delayMs = delaySec * 1000
            LogWriter.log("Запланирован $pkg через ${delaySec}с от старта")
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
                LogWriter.log("+ Запущен $pkg (задержка ${delaySec}с)")
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
        val pi = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

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
        LogWriter.log("=== Сервис остановлен ===")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
