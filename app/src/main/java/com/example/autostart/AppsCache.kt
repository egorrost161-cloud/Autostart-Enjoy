package com.example.autostart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Простой кэш установленных приложений на диск.
 * Хранит: label, package, icon (base64 PNG).
 * Файл: filesDir/apps_cache.json
 */
class AppsCache(private val context: Context) {

    companion object {
        private const val CACHE_FILE = "apps_cache.json"
        private const val VERSION = 1
        private const val ICON_SIZE = 96 // px, уменьшаем иконку для экономии места
    }

    data class Entry(
        val label: String,
        val pkg: String,
        val iconBase64: String
    )

    private val file: File
        get() = File(context.filesDir, CACHE_FILE)

    fun exists(): Boolean = file.exists()

    /** Сохраняет список приложений в кэш. */
    fun save(entries: List<Entry>) {
        try {
            val root = JSONObject()
            root.put("version", VERSION)
            root.put("saved_at", System.currentTimeMillis())

            val arr = JSONArray()
            for (e in entries) {
                val obj = JSONObject()
                obj.put("label", e.label)
                obj.put("pkg", e.pkg)
                obj.put("icon", e.iconBase64)
                arr.put(obj)
            }
            root.put("apps", arr)

            file.writeText(root.toString())
        } catch (e: Exception) {
            LogWriter.log("AppsCache.save error: ${e.message}")
        }
    }

    /** Читает кэш. Возвращает null, если файла нет или он повреждён. */
    fun load(): List<Entry>? {
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            val root = JSONObject(text)

            val version = root.optInt("version", 0)
            if (version != VERSION) {
                LogWriter.log("AppsCache: version mismatch ($version != $VERSION), ignoring")
                return null
            }

            val arr = root.optJSONArray("apps") ?: return null
            val result = ArrayList<Entry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    Entry(
                        label = obj.optString("label", ""),
                        pkg = obj.optString("pkg", ""),
                        iconBase64 = obj.optString("icon", "")
                    )
                )
            }
            result
        } catch (e: Exception) {
            LogWriter.log("AppsCache.load error: ${e.message}")
            null
        }
    }

    fun clear() {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            LogWriter.log("AppsCache.clear error: ${e.message}")
        }
    }

    /** Кодирует Drawable в base64 PNG, предварительно уменьшив до [ICON_SIZE]. */
    fun encodeIcon(drawable: Drawable): String {
        return try {
            val bmp = drawableToBitmap(drawable, ICON_SIZE)
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val bytes = baos.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            LogWriter.log("AppsCache.encodeIcon error: ${e.message}")
            ""
        }
    }

    /** Декодирует base64 PNG обратно в Drawable. */
    fun decodeIcon(base64: String): Drawable? {
        if (base64.isEmpty()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            BitmapDrawable(context.resources, bmp)
        } catch (e: Exception) {
            LogWriter.log("AppsCache.decodeIcon error: ${e.message}")
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            // Скейлим до size x size
            val scaled = Bitmap.createScaledBitmap(src, size, size, true)
            // Если скейлили в новую — старую (если это копия) переработать нельзя.
            return scaled
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }
}
