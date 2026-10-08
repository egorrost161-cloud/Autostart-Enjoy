package com.example.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        LogWriter.init(context)
        LogWriter.log("SystemEvent: $action")

        when (action) {

            // === 1-3. Загрузка системы — обычный запуск с задержкой ===
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                LogWriter.log("BOOT событие — стартуем сервис")
                startService(context, forceCheck = false)
            }

            // === 4-5. Пробуждение экрана — форс-проверка ===
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT -> {
                LogWriter.log("Экран включён / разблокирован — форс-проверка")
                startService(context, forceCheck = true)
            }

            // === 6. Зарядка подключена — форс-проверка ===
            Intent.ACTION_POWER_CONNECTED -> {
                LogWriter.log("Зарядка подключена — форс-проверка")
                startService(context, forceCheck = true)
            }

            // === 7-9. Сеть изменилась — форс-проверка ===
            "android.net.wifi.STATE_CHANGE",
            "android.net.conn.CONNECTIVITY_CHANGE",
            "android.net.wifi.WIFI_STATE_CHANGED" -> {
                LogWriter.log("Сеть изменилась — форс-проверка")
                startService(context, forceCheck = true)
            }

            // === 10. Гарнитура подключена — форс-проверка ===
            Intent.ACTION_HEADSET_PLUG -> {
                LogWriter.log("Гарнитура подключена — форс-проверка")
                startService(context, forceCheck = true)
            }

            // === 11. SD-карта смонтирована — форс-проверка ===
            Intent.ACTION_MEDIA_MOUNTED -> {
                LogWriter.log("SD-карта смонтирована — форс-проверка")
                startService(context, forceCheck = true)
            }

            // === 12-13. Прочие события — только логируем ===
            Intent.ACTION_SCREEN_OFF,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_AIRPLANE_MODE_CHANGED,
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_REMOVED -> {
                LogWriter.log("Прочее событие — игнорируем")
            }

            else -> {
                LogWriter.log("Неизвестное событие — игнорируем")
            }
        }
    }

    /**
     * Запускает KeepAliveService.
     * Если [forceCheck] = true — сервис немедленно запускает приложения,
     * без ожидания задержки.
     */
    private fun startService(context: Context, forceCheck: Boolean) {
        val svc = Intent(context, KeepAliveService::class.java)
        if (forceCheck) {
            svc.putExtra("FORCE_CHECK", true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
