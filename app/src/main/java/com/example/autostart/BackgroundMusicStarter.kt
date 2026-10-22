package com.example.autostart

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import kotlin.concurrent.thread

object BackgroundMusicStarter {
    private const val TARGET = "ru.yandex.music"

    fun start(context: Context, delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            thread { tryOnce(context.applicationContext) }
        }, delayMs)
    }

    private fun tryOnce(context: Context) {
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (flat == null || !flat.contains(context.packageName)) {
            LogWriter.log("Starter: Нет доступа к уведомлениям")
            return
        }

        LogWriter.log("Starter: Попытка мягкого запуска")
        
        try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(context, MediaAccessService::class.java)
            val sessions = manager.getActiveSessions(listener)

            var targetController: MediaController? = null
            for (c in sessions) {
                if (TARGET == c.packageName) {
                    targetController = c
                    break
                }
            }

            // Если сессия уже есть — просто жмем Play
            if (targetController != null) {
                if (targetController.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    LogWriter.log("Starter: Уже играет")
                    return
                }
                targetController.transportControls.play()
                LogWriter.log("Starter: Отправлен PLAY через MediaController")
            } else {
                // Если сессии нет — будим через MediaBrowser
                val intent = Intent("android.media.browse.MediaBrowserService").setPackage(TARGET)
                val resolveInfos = context.packageManager.queryIntentServices(intent, 0)
                if (!resolveInfos.isNullOrEmpty()) {
                    val info = resolveInfos[0].serviceInfo
                    val comp = ComponentName(info.packageName, info.name)
                    
                    Handler(Looper.getMainLooper()).post {
                        val callback = object : MediaBrowser.ConnectionCallback() {
                            override fun onConnected() {
                                try {
                                    val browser = browsers[comp] ?: return
                                    val token = browser.sessionToken ?: return
                                    val controller = MediaController(context, token)
                                    controller.transportControls.play()
                                    LogWriter.log("Starter: Отправлен PLAY через Browser")
                                } catch (e: Exception) {
                                    LogWriter.log("Starter Ошибка Browser: ${e.message}")
                                }
                            }
                            override fun onConnectionFailed() {
                                LogWriter.log("Starter: Browser connect failed")
                            }
                        }
                        val browser = MediaBrowser(context, comp, callback, null)
                        browsers[comp] = browser
                        browser.connect()
                    }
                }
            }
        } catch (e: Exception) {
            LogWriter.log("Starter Ошибка: ${e.message}")
        }
    }

    private val browsers = mutableMapOf<ComponentName, MediaBrowser>()
}
