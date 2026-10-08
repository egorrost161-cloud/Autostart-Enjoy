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
            // Загрузка — не форсируем, пусть BootReceiver отработает
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                LogWriter.log("BOOT событие — стартуем сервис")
                startService(context, forceCheck = false)
            }

            // Пробуждение экрана — форсируем
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT -> {
                LogWriter.log("Экран включён — форс-проверка")
                startService(context, forceCheck = true)
            }

            // Зарядка подключена — форсируем
            Intent.ACTION_POWER_CONNECTED -> {
                LogWriter.log("Зарядка подключена — форс-проверка")
                startService(context, forceCheck = true)
            }

            // Wi-Fi / сеть — форсируем
            "android.net.wifi.STATE_CHANGE",
            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                LogWriter.log("Сеть изменилась — форс-проверка")
                startService(context, forceCheck = true)
            }

            else -> {
                LogWriter.log("Прочее событие — игнорируем")
            }
        }
    }

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
