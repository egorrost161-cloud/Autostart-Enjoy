package com.example.autostart

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogWriter {
    private const val TAG = "AutoStart"
    private const val LOG_FILE = "autostart_log.txt"
    private var appContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun log(message: String) {
        Log.i(TAG, message)
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, LOG_FILE)
            val timestamp = dateFormat.format(Date())
            file.appendText("$timestamp  $message\n")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи лога: ${e.message}")
        }
    }
}
