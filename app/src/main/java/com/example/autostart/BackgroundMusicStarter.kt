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
    private const val TIMEOUT = 60000L
    private const val RETRY = 1500L
    private const val KEYCODE_MEDIA_PLAY = 126

    @Volatile
    var lastStatus: String = ""
        private set

    fun start(context: Context, delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            thread { loop(context.applicationContext) }
        }, delayMs)
    }

    private fun loop(context: Context) {
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (flat == null || !flat.contains(context.packageName)) {
            note(context, "Нет доступа к уведомлениям! Выдай разрешение.")
            return
        }

        note(context, "Старт: ищу Яндекс.Музыку")
        val start = SystemClock.elapsedRealtime()
        var browserAttempts = 0

        while (SystemClock.elapsedRealtime() - start < TIMEOUT) {
            try {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val listener = ComponentName(context, MediaAccessService::class.java)
                val sessions = try { manager.getActiveSessions(listener) } catch (e: Exception) { emptyList() }

                var targetController: MediaController? = null
                var alreadyPlaying = false

                for (c in sessions) {
                    if (TARGET == c.packageName) {
                        targetController = c
                        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) alreadyPlaying = true
                        break
                    }
                }

                if (alreadyPlaying) {
                    note(context, "Уже играет — всё ок")
                    return
                }

                // МЕТОД 1: Мгновенный Play
                if (targetController != null) {
                    try {
                        targetController.transportControls.play()
                        note(context, "Метод 1: PLAY через MediaController")
                        Thread.sleep(1500)
                        if (targetController.playbackState?.state == PlaybackState.STATE_PLAYING) {
                            note(context, "УСПЕХ (Метод 1)")
                            return
                        }
                    } catch (e: Exception) { note(context, "Метод 1 ошибка: ${e.message}") }
                }

                // МЕТОД 2: Broadcast
                if (targetController == null) {
                    val receiver = findMediaButtonReceiver(context)
                    if (receiver != null) {
                        sendMediaButton(context, receiver)
                        note(context, "Метод 2: MEDIA_BUTTON")
                    }
                }

                // МЕТОД 3: Холодный старт
                if (targetController == null && browserAttempts < 3) {
                    browserAttempts++
                    val ok = coldStart(context)
                    note(context, "Метод 3: MediaBrowser connect #$browserAttempts = $ok")
                }

                Thread.sleep(RETRY)
            } catch (e: Exception) {
                note(context, "Ошибка цикла: ${e.message}")
                Thread.sleep(RETRY)
            }
        }
        note(context, "Время вышло — музыка не заиграла")
    }

    private fun findMediaButtonReceiver(context: Context): ComponentName? {
        try {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(TARGET)
            val receivers = context.packageManager.queryBroadcastReceivers(intent, 0)
            for (ri in receivers) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == TARGET && ai.exported && ai.enabled) {
                    if (ai.permission == null || context.checkSelfPermission(ai.permission) == PackageManager.PERMISSION_GRANTED) {
                        return ComponentName(ai.packageName, ai.name)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun sendMediaButton(context: Context, target: ComponentName) {
        try {
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KEYCODE_MEDIA_PLAY, 0)
            val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KEYCODE_MEDIA_PLAY, 0)
            val baseIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply { component = target }

            context.sendBroadcast(Intent(baseIntent).putExtra(Intent.EXTRA_KEY_EVENT, down))
            context.sendBroadcast(Intent(baseIntent).putExtra(Intent.EXTRA_KEY_EVENT, up))
        } catch (e: Exception) { note(context, "sendMediaButton: ${e.message}") }
    }

    private fun coldStart(context: Context): Boolean {
        var result = false
        val latch = java.util.concurrent.CountDownLatch(1)
        
        Handler(Looper.getMainLooper()).post {
            try {
                val intent = Intent("android.media.browse.MediaBrowserService").setPackage(TARGET)
                val resolveInfos = context.packageManager.queryIntentServices(intent, 0)
                if (resolveInfos.isNullOrEmpty()) {
                    note(context, "MediaBrowserService не найден")
                    latch.countDown()
                    return@post
                }
                val info = resolveInfos[0].serviceInfo
                val comp = ComponentName(info.packageName, info.name)

                val callback = object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        try {
                            val browser = browsers[comp] ?: return
                            val token = browser.sessionToken ?: return
                            val controller = MediaController(context, token)
                            controller.transportControls.play()
                            note(context, "Метод 3 onConnected: PLAY УСПЕХ!")
                        } catch (e: Exception) { note(context, "Метод 3 onConnected: ${e.message}") }
                    }
                    override fun onConnectionFailed() { note(context, "Метод 3: connect failed") }
                }

                val browser = MediaBrowser(context, comp, callback, null)
                browsers[comp] = browser
                browser.connect()
                result = true
            } catch (e: Exception) {
                note(context, "Метод 3 connect: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
        
        try { latch.await(2, java.util.concurrent.TimeUnit.SECONDS) } catch (e: InterruptedException) {}
        return result
    }

    private val browsers = mutableMapOf<ComponentName, MediaBrowser>()

    private fun note(context: Context, msg: String) {
        lastStatus = msg
        LogWriter.log("Starter: $msg")
    }
}
