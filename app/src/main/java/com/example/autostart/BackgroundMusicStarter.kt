package com.example.autostart

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
    private const val TIMEOUT = 45000L // 45 секунд на попытки
    private const val RETRY = 2000L // каждые 2 секунды

    fun start(context: Context, delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            thread { loop(context) }
        }, delayMs)
    }

    private fun loop(context: Context) {
        // Проверяем, выдал ли пользователь доступ к уведомлениям
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (flat == null || !flat.contains(context.packageName)) {
            LogWriter.log("Starter: ОШИБКА! Нет доступа к уведомлениям.")
            return
        }

        val start = SystemClock.elapsedRealtime()
        var sentBrowser = false

        while (SystemClock.elapsedRealtime() - start < TIMEOUT) {
            try {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val listener = ComponentName(context, MediaAccessService::class.java)
                val sessions = manager.getActiveSessions(listener)
                var targetController: MediaController? = null

                // Метод 1: Ищем активную сессию музыки
                for (c in sessions) {
                    if (TARGET == c.packageName) {
                        targetController = c
                        break
                    }
                }

                if (targetController != null) {
                    val state = targetController.playbackState
                    if (state != null) {
                        if (state.state == PlaybackState.STATE_PLAYING) return // Уже играет
                        
                        targetController.transportControls.play()
                        Thread.sleep(1500)
                        
                        if (targetController.playbackState?.state == PlaybackState.STATE_PLAYING) {
                            LogWriter.log("Starter: УСПЕХ (Метод 1 - Прямой контроллер)")
                            return
                        }
                    }
                }

                // Метод 2: Эмуляция кнопки гарнитуры (если сессия мертва)
                if (targetController == null) {
                    val mediaIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
                    mediaIntent.setPackage(TARGET)
                    val now = SystemClock.uptimeMillis()
                    val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
                    val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
                    mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT, down)
                    context.sendBroadcast(mediaIntent)
                    mediaIntent.putExtra(Intent.EXTRA_KEY_EVENT, up)
                    context.sendBroadcast(mediaIntent)
                    LogWriter.log("Starter: Отправлена кнопка MEDIA_PLAY")
                }

                // Метод 3: Холодный старт через MediaBrowserService
                if (targetController == null && !sentBrowser) {
                    val intent = Intent("android.media.browse.MediaBrowserService").setPackage(TARGET)
                    val resolveInfos = context.packageManager.queryIntentServices(intent, 0)
                    if (!resolveInfos.isNullOrEmpty()) {
                        val info = resolveInfos[0].serviceInfo
                        val comp = ComponentName(info.packageName, info.name)
                        val browser = MediaBrowser(context, comp, object : MediaBrowser.ConnectionCallback() {
                            override fun onConnected() {
                                try {
                                    browser.mediaController?.transportControls?.play()
                                    LogWriter.log("Starter: УСПЕХ (Метод 3 - MediaBrowser)")
                                } catch (e: Exception) {
                                    LogWriter.log("Starter Ошибка Browser: ${e.message}")
                                }
                            }
                        }, null)
                        browser.connect()
                        sentBrowser = true
                    }
                }
                Thread.sleep(RETRY)
            } catch (e: Exception) {
                LogWriter.log("Starter Ошибка: ${e.message}")
                Thread.sleep(RETRY)
            }
        }
        LogWriter.log("Starter: Время вышло, музыка не заиграла.")
    }
}
