package com.example.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            LogWriter.log("BootReceiver: Получен сигнал загрузки системы")

            // 1. Запускаем ваш сервис для поддержания жизни и запуска приложений
            val serviceIntent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 2. Запускаем автоплей Яндекс.Музыки (если включено в настройках)
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("autoplay_yandex", false)) {
                LogWriter.log("BootReceiver: Автоплей включен, запускаем через 15 сек...")
                // Задержка 15 секунд, чтобы система и магнитола полностью прогрузились
                BackgroundMusicStarter.start(context, 15000L)
            }
        }
    }
}
