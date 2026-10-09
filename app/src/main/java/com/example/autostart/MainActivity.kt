package com.example.autostart

import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var appsCache: AppsCache
    private var showingSetup = false
    private var lastPermissionState = ""

    // Кэш приложений (в памяти + на диск)
    private data class AppEntry(
        val label: String,
        val icon: Drawable?,
        val pkg: String
    )
    private var cachedApps: List<AppEntry>? = null
    private var isLoadingApps = false

    // Ссылки на главный экран
    private var mainScrollView: ScrollView? = null
    private var mainRoot: LinearLayout? = null
    private var listTitleView: View? = null

    // Отложенная пересортировка
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRebuild: Runnable? = null

    // Цвета
    private val colorBg = Color.parseColor("#F5F5F7")
    private val colorCard = Color.WHITE
    private val colorGreen = Color.parseColor("#4CAF50")
    private val colorRed = Color.parseColor("#E53935")
    private val colorGray = Color.parseColor("#EEEEEE")
    private val colorText = Color.parseColor("#212121")
    private val colorTextLight = Color.parseColor("#616161")
    private val colorBorder = Color.parseColor("#E0E0E0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        appsCache = AppsCache(this)
        LogWriter.init(this)

        // 1) Пробуем мгновенно загрузить из кэша на диске
        val cachedFromDisk = appsCache.load()
        if (cachedFromDisk != null) {
            cachedApps = cachedFromDisk.mapNotNull { entry ->
                val icon = appsCache.decodeIcon(entry.iconBase64)
                AppEntry(entry.label, icon, entry.pkg)
            }
            LogWriter.log("AppsCache: загружено ${cachedApps?.size} приложений из кэша")
        }

        if (!areAllPermissionsGranted()) {
            showingSetup = true
            showSetupScreen()
        } else {
            showingSetup = false
            showMainScreen()
            // 2) В фоне проверяем актуальность кэша
            refreshAppsInBackground()
        }
        lastPermissionState = permissionStateKey()
    }

    override fun onResume() {
        super.onResume()
        if (showingSetup) {
            val current = permissionStateKey()
            if (current != lastPermissionState) {
                lastPermissionState = current
                recreate()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * В фоне собирает актуальный список приложений. Если он отличается
     * от кэша — обновляет кэш и UI.
     */
    private fun refreshAppsInBackground() {
        if (isLoadingApps) return
        isLoadingApps = true

        Thread {
            val pm = packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            val launchable = apps.filter { app ->
                app.packageName != packageName &&
                pm.getLaunchIntentForPackage(app.packageName) != null
            }

            // Собираем новую версию списка и сразу — сериализуем в кэш
            val cacheEntries = ArrayList<AppsCache.Entry>(launchable.size)
            val runtimeList = ArrayList<AppEntry>(launchable.size)

            for (app in launchable) {
                try {
                    val label = pm.getApplicationLabel(app).toString()
                    val icon = pm.getApplicationIcon(app)
                    val iconBase64 = appsCache.encodeIcon(icon)
                    cacheEntries.add(AppsCache.Entry(label, app.packageName, iconBase64))
                    runtimeList.add(AppEntry(label, icon, app.packageName))
                } catch (e: Exception) {
                    LogWriter.log("refreshApps: ошибка ${app.packageName}: ${e.message}")
                }
            }

            // Проверяем, отличается ли новый список от того, что уже в памяти
            val current = cachedApps
            val changed = current == null ||
                current.size != runtimeList.size ||
                current.map { it.pkg }.toSet() != runtimeList.map { it.pkg }.toSet()

            if (changed) {
                appsCache.save(cacheEntries)
                LogWriter.log("AppsCache: кэш обновлён (${runtimeList.size} приложений)")
            }

            runOnUiThread {
                cachedApps = runtimeList
                isLoadingApps = false
                if (!showingSetup && changed) {
                    rebuildMainContent()
                }
            }
        }.start()
    }

    // ============ УТИЛИТЫ UI ============

    private fun roundedBg(color: Int, radiusPx: Int = 24): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx.toFloat()
        }
    }

    private fun roundedBgWithBorder(fill: Int, border: Int, radiusPx: Int = 24): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusPx.toFloat()
            setStroke(2, border)
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
            background = roundedBg(colorCard, 32)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 12, 0, 12) }
        }
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(colorText)
        setPadding(0, 12, 0, 4)
    }

    private fun hint(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(colorTextLight)
        setPadding(0, 4, 0, 8)
    }

    private fun inputField(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        textSize = 14f
        setTextColor(colorText)
        setPadding(24, 20, 24, 20)
        background = roundedBgWithBorder(colorBg, colorBorder, 20)
    }

    private fun roundedButton(text: String, bgColor: Int, textColor: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(textColor)
            isAllCaps = false
            background = roundedBg(bgColor, 24)
            setPadding(20, 30, 20, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
            setOnClickListener { onClick() }
        }

    // ============ РАЗРЕШЕНИЯ ============

    private fun permissionStateKey(): String {
        return "${Settings.canDrawOverlays(this)}|${hasUsageStatsPermission()}|${batteryGranted()}|${areNotificationsEnabled()}"
    }

    private fun areAllPermissionsGranted(): Boolean {
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true
        val battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
        val usageStats = hasUsageStatsPermission()
        val notifications = areNotificationsEnabled()
        return overlay && battery && usageStats && notifications
    }

    private fun hasUsageStatsPermission(): Boolean {
        try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            return false
        }
    }

    private fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun overlayGranted(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)

    private fun batteryGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallback = Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                fallback.putExtra("app_package", packageName)
                fallback.putExtra("app_uid", applicationInfo.uid)
                startActivity(fallback)
            } catch (e2: Exception) {
                Toast.makeText(this@MainActivity, "Не удалось открыть настройки уведомлений", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun permissionButton(title: String, granted: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = if (granted) "✓  $title" else "○  $title"
            textSize = 15f
            setTextColor(if (granted) Color.WHITE else colorText)
            isAllCaps = false
            background = roundedBg(if (granted) colorGreen else colorGray, 24)
            setPadding(20, 30, 20, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
            setOnClickListener { onClick() }
        }

    // ============ ЭКРАН НАСТРОЙКИ ============

    private fun showSetupScreen() {
        showingSetup = true
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 40, 30, 40)
            setBackgroundColor(colorBg)
        }

        root.addView(TextView(this).apply {
            text = "AutoStart Pro"
            textSize = 28f
            setTextColor(colorText)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 10)
        })

        root.addView(TextView(this).apply {
            text = "Для работы нужно 4 разрешения.\nНажми на каждое — откроется системная настройка.\nКогда галочка станет зелёной — жми «ПРОДОЛЖИТЬ»."
            textSize = 14f
            setTextColor(colorTextLight)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 30)
        })

        val permsCard = card()
        permsCard.addView(permissionButton("Наложение поверх окон", overlayGranted()) {
            if (!overlayGranted()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                }
            } else {
                Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
            }
        })
        permsCard.addView(permissionButton("Статистика использования", hasUsageStatsPermission()) {
            if (!hasUsageStatsPermission()) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                Toast.makeText(this@MainActivity, "Найди AutoStart Pro и включи доступ", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
            }
        })
        permsCard.addView(permissionButton("Игнор батареи", batteryGranted()) {
            if (!batteryGranted()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = android.net.Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            } else {
                Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
            }
        })
        permsCard.addView(permissionButton("Уведомления", areNotificationsEnabled()) {
            if (!areNotificationsEnabled()) {
                openNotificationSettings()
                Toast.makeText(this@MainActivity, "Включи уведомления для AutoStart Pro", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(permsCard)

        root.addView(roundedButton("ОБНОВИТЬ СТАТУС", colorGray, colorText) { recreate() })
        root.addView(roundedButton("ПРОДОЛЖИТЬ", colorGreen, Color.WHITE) {
            if (areAllPermissionsGranted()) {
                prefs.edit().putBoolean("setup_done", true).apply()
            } else {
                Toast.makeText(this@MainActivity, "Не все разрешения выданы", Toast.LENGTH_SHORT).show()
            }
            recreate()
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(colorBg)
            addView(root)
        })
    }

    // ============ ГЛАВНЫЙ ЭКРАН ============

    private fun showMainScreen() {
        showingSetup = false
        if (mainScrollView == null) {
            mainScrollView = ScrollView(this).apply {
                setBackgroundColor(colorBg)
            }
            mainRoot = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(30, 40, 30, 40)
                setBackgroundColor(colorBg)
            }
            (mainScrollView as ScrollView).addView(mainRoot)
            setContentView(mainScrollView)
        }
        rebuildMainContent()
    }

    private fun rebuildMainContent() {
        val root = mainRoot ?: return
        root.removeAllViews()

        root.addView(TextView(this).apply {
            text = "AutoStart Pro"
            textSize = 26f
            setTextColor(colorText)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
        })

        // ============ КАРТОЧКА: СВОРАЧИВАНИЕ ============
        val delayCard = card()
        delayCard.addView(label("Пауза перед сворачиванием (сек)"))
        delayCard.addView(inputField("1.5").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            val savedSec = prefs.getFloat("bg_go_home_delay_sec", 1.5f)
            setText(formatSeconds(savedSec))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val sec = s?.toString()?.replace(',', '.')?.toFloatOrNull() ?: return
                    if (sec < 0.5f || sec > 10.0f) return
                    prefs.edit().putFloat("bg_go_home_delay_sec", sec).apply()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        })
        delayCard.addView(hint("Диапазон: 0.5 – 10 сек"))
        root.addView(delayCard)

        // ============ КАРТОЧКА: АВТОПЛЕЙ ============
        val autoplayCard = card()
        autoplayCard.addView(CheckBox(this).apply {
            text = "Автоплей Яндекс.Музыки"
            textSize = 15f
            setTextColor(colorText)
            isChecked = prefs.getBoolean("autoplay_yandex", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("autoplay_yandex", checked).apply()
            }
        })
        autoplayCard.addView(hint("Пробует отправить команду Play через 3 и 6 секунд после запуска"))
        root.addView(autoplayCard)

        // ============ КАРТОЧКА: МОНИТОР (СО СПОЙЛЕРОМ) ============
        val monitorCard = card()
        val monitorOpen = prefs.getBoolean("monitor_spoiler_open", false)

        val spoilerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val newState = !prefs.getBoolean("monitor_spoiler_open", false)
                prefs.edit().putBoolean("monitor_spoiler_open", newState).apply()
                rebuildMainContent()
            }
        }
        spoilerHeader.addView(TextView(this).apply {
            text = "Монитор лаунчера"
            textSize = 16f
            setTextColor(colorText)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        spoilerHeader.addView(TextView(this).apply {
            text = if (monitorOpen) "▲" else "▼"
            textSize = 14f
            setTextColor(colorTextLight)
        })
        monitorCard.addView(spoilerHeader)

        if (monitorOpen) {
            val monitorOn = prefs.getBoolean("monitor_enabled", false)

            monitorCard.addView(CheckBox(this).apply {
                text = "Включить монитор"
                textSize = 15f
                setTextColor(colorText)
                isChecked = monitorOn
                setPadding(0, 20, 0, 0)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("monitor_enabled", checked).apply()
                    rebuildMainContent()
                }
            })

            val packageCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 24, 24, 24)
                background = roundedBgWithBorder(colorBg, colorBorder, 20)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 12, 0, 12) }
            }

            val savedMonitorPkg = prefs.getString("monitor_package", "") ?: ""
            if (savedMonitorPkg.isNotEmpty()) {
                val exists = try {
                    packageManager.getApplicationInfo(savedMonitorPkg, 0)
                    true
                } catch (e: Exception) { false }

                if (exists) {
                    val appInfo = packageManager.getApplicationInfo(savedMonitorPkg, 0)
                    val labelStr = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)

                    packageCard.addView(ImageView(this).apply {
                        setImageDrawable(icon)
                        layoutParams = LinearLayout.LayoutParams(80, 80).apply { setMargins(0, 0, 20, 0) }
                    })

                    val textCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    textCol.addView(TextView(this).apply {
                        text = labelStr
                        textSize = 16f
                        setTextColor(colorText)
                    })
                    textCol.addView(TextView(this).apply {
                        text = savedMonitorPkg
                        textSize = 11f
                        setTextColor(colorTextLight)
                    })
                    packageCard.addView(textCol)

                    packageCard.addView(TextView(this).apply {
                        text = "✕"
                        textSize = 26f
                        setTextColor(colorRed)
                        gravity = Gravity.CENTER
                        setPadding(20, 10, 20, 10)
                        setOnClickListener {
                            prefs.edit().remove("monitor_package").apply()
                            Toast.makeText(this@MainActivity, "Пакет монитора очищен", Toast.LENGTH_SHORT).show()
                            rebuildMainContent()
                        }
                    })
                } else {
                    packageCard.addView(TextView(this).apply {
                        text = "⚠  Приложение удалено — нажми, чтобы выбрать заново"
                        textSize = 14f
                        setTextColor(colorRed)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    packageCard.addView(TextView(this).apply {
                        text = "✕"
                        textSize = 26f
                        setTextColor(colorRed)
                        gravity = Gravity.CENTER
                        setPadding(20, 10, 20, 10)
                        setOnClickListener {
                            prefs.edit().remove("monitor_package").apply()
                            rebuildMainContent()
                        }
                    })
                }
            } else {
                packageCard.addView(TextView(this).apply {
                    text = "Не выбрано — нажми, чтобы выбрать приложение"
                    textSize = 14f
                    setTextColor(colorTextLight)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }

            packageCard.isClickable = true
            packageCard.isFocusable = true
            packageCard.setOnClickListener {
                showPackagePickerDialog { pkg ->
                    prefs.edit().putString("monitor_package", pkg).apply()
                    rebuildMainContent()
                }
            }
            monitorCard.addView(packageCard)

            monitorCard.addView(label("Интервал проверки (сек)"))
            monitorCard.addView(inputField("30").apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(prefs.getLong("monitor_interval_sec", 30L).toString())
                isEnabled = monitorOn
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        val v = s?.toString()?.toLongOrNull() ?: return
                        if (v in 5L..300L) prefs.edit().putLong("monitor_interval_sec", v).apply()
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            })
            monitorCard.addView(hint("5 – 300 сек"))

            monitorCard.addView(label("Неактивность порог (мин)"))
            monitorCard.addView(inputField("30").apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(prefs.getLong("monitor_idle_minutes", 30L).toString())
                isEnabled = monitorOn
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        val v = s?.toString()?.toLongOrNull() ?: return
                        if (v in 1L..240L) prefs.edit().putLong("monitor_idle_minutes", v).apply()
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            })
            monitorCard.addView(hint("1 – 240 мин. Монитор вернёт лаунчер, если он неактивен N минут"))
        }
        root.addView(monitorCard)

        // ============ КНОПКИ ============
        root.addView(roundedButton("ЗАПУСТИТЬ СЕРВИС СЕЙЧАС", colorGreen, Color.WHITE) {
            val intent = Intent(this@MainActivity, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this@MainActivity, "Сервис запущен", Toast.LENGTH_SHORT).show()
        })

        root.addView(roundedButton("ОТКЛЮЧИТЬ АВТОЗАПУСК", colorRed, Color.WHITE) {
            prefs.edit().remove("target_packages").apply()
            Toast.makeText(this@MainActivity, "Отключено", Toast.LENGTH_SHORT).show()
            rebuildMainContent()
        })

        root.addView(roundedButton("ПОКАЗАТЬ ЛОГ", colorGray, colorText) { showLogDialog() })
        root.addView(roundedButton("ОЧИСТИТЬ ЛОГ", colorGray, colorText) {
            try {
                val file = File(filesDir, "autostart_log.txt")
                if (file.exists()) file.delete()
                Toast.makeText(this@MainActivity, "Лог очищен", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // ============ СПИСОК ПРИЛОЖЕНИЙ ============
        val listTitle = TextView(this).apply {
            text = "ВЫБЕРИТЕ ПРИЛОЖЕНИЯ"
            textSize = 13f
            setTextColor(colorTextLight)
            letterSpacing = 0.1f
            setPadding(10, 30, 10, 10)
        }
        root.addView(listTitle)
        listTitleView = listTitle

        root.addView(TextView(this).apply {
            text = "Тап по строке — выбор. Фон — свернуть после запуска."
            textSize = 12f
            setTextColor(colorTextLight)
            setPadding(10, 0, 10, 10)
        })

        val apps = cachedApps
        if (apps == null) {
            // Очень редкая ситуация — если кэш на диске пустой И фоновая загрузка ещё не завершилась
            val loadingView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(20, 60, 20, 60)
            }
            loadingView.addView(ProgressBar(this).apply {
                layoutParams = LinearLayout.LayoutParams(80, 80)
            })
            loadingView.addView(TextView(this).apply {
                text = "  Загрузка приложений…"
                textSize = 14f
                setTextColor(colorTextLight)
            })
            root.addView(loadingView)
            return
        }

        val savedPackages = prefs.getStringSet("target_packages", emptySet()) ?: emptySet()

        val sorted = apps.sortedWith(
            compareByDescending<AppEntry> { savedPackages.contains(it.pkg) }
                .thenBy { it.label.lowercase() }
        )

        for (entry in sorted) {
            val isSelected = savedPackages.contains(entry.pkg)
            root.addView(createAppRow(entry, isSelected))
        }
    }

    // ============ СТРОКА ПРИЛОЖЕНИЯ ============

    private fun formatSeconds(sec: Float): String {
        return if (sec % 1.0f == 0.0f) sec.toInt().toString() else sec.toString()
    }

    private fun createAppRow(entry: AppEntry, isSelected: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 24, 24, 24)
            background = roundedBg(if (isSelected) colorGreen else colorCard, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
        }

        row.addView(ImageView(this).apply {
            if (entry.icon != null) {
                setImageDrawable(entry.icon)
            } else {
                setBackgroundColor(colorGray)
            }
            layoutParams = LinearLayout.LayoutParams(90, 90).apply { setMargins(0, 0, 20, 0) }
        })

        val labelView = TextView(this).apply {
            text = if (isSelected) "✓  ${entry.label}" else "○  ${entry.label}"
            textSize = 16f
            setTextColor(if (isSelected) Color.WHITE else colorText)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelView)

        val delayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getLong("delay_${entry.pkg}", 15L).toString())
            textSize = 14f
            width = 140
            hint = "15"
            setPadding(20, 15, 20, 15)
            setTextColor(if (isSelected) Color.WHITE else colorText)
            background = roundedBgWithBorder(
                if (isSelected) Color.parseColor("#66FFFFFF") else colorBg,
                if (isSelected) Color.parseColor("#88FFFFFF") else colorBorder,
                16
            )
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val delay = s?.toString()?.toLongOrNull()
                    if (delay != null && delay in 0L..600L) {
                        prefs.edit().putLong("delay_${entry.pkg}", delay).apply()
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        row.addView(delayInput)

        val bgCheck = CheckBox(this).apply {
            text = "Фон"
            textSize = 12f
            setTextColor(if (isSelected) Color.WHITE else colorText)
            isChecked = prefs.getBoolean("bg_${entry.pkg}", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("bg_${entry.pkg}", checked).apply()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 0, 0) }
        }
        row.addView(bgCheck)

        row.setOnClickListener {
            val currentSet = prefs.getStringSet("target_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
            val nowSelected: Boolean
            if (currentSet.contains(entry.pkg)) {
                currentSet.remove(entry.pkg)
                nowSelected = false
                Toast.makeText(this@MainActivity, "Автозапуск отключён", Toast.LENGTH_SHORT).show()
            } else {
                currentSet.add(entry.pkg)
                nowSelected = true
                Toast.makeText(this@MainActivity, "Выбрано: ${entry.label}", Toast.LENGTH_SHORT).show()
            }
            prefs.edit().putStringSet("target_packages", currentSet).apply()

            row.background = roundedBg(if (nowSelected) colorGreen else colorCard, 24)
            labelView.text = if (nowSelected) "✓  ${entry.label}" else "○  ${entry.label}"
            labelView.setTextColor(if (nowSelected) Color.WHITE else colorText)
            delayInput.setTextColor(if (nowSelected) Color.WHITE else colorText)
            delayInput.background = roundedBgWithBorder(
                if (nowSelected) Color.parseColor("#66FFFFFF") else colorBg,
                if (nowSelected) Color.parseColor("#88FFFFFF") else colorBorder,
                16
            )
            bgCheck.setTextColor(if (nowSelected) Color.WHITE else colorText)

            pendingRebuild?.let { handler.removeCallbacks(it) }
            pendingRebuild = Runnable {
                rebuildMainContent()
                mainScrollView?.post {
                    val target = listTitleView ?: return@post
                    mainScrollView?.smoothScrollTo(0, target.top - 20)
                }
            }
            handler.postDelayed(pendingRebuild!!, 600L)
        }

        return row
    }

    // ============ ДИАЛОГИ ============

    private fun showLogDialog() {
        try {
            val file = File(filesDir, "autostart_log.txt")
            val text = if (file.exists()) file.readText() else "Лог пуст — событий ещё не было."

            val scroll = ScrollView(this)
            val textView = TextView(this).apply {
                this.text = text
                textSize = 11f
                setPadding(20, 20, 20, 20)
                setTextIsSelectable(true)
                setTextColor(colorText)
            }
            scroll.addView(textView)

            AlertDialog.Builder(this)
                .setTitle("Лог AutoStart Pro")
                .setView(scroll)
                .setPositiveButton("Закрыть", null)
                .setNeutralButton("Очистить") { _, _ ->
                    file.delete()
                    Toast.makeText(this@MainActivity, "Лог очищен", Toast.LENGTH_SHORT).show()
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "Ошибка чтения лога: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPackagePickerDialog(onPicked: (String) -> Unit) {
        val apps = cachedApps ?: emptyList()
        if (apps.isEmpty()) {
            Toast.makeText(this, "Приложения ещё загружаются, попробуйте через секунду", Toast.LENGTH_SHORT).show()
            return
        }
        val sorted = apps.sortedBy { it.label.lowercase() }
        val labels = sorted.map { it.label }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите приложение для монитора")
            .setItems(labels) { _, which ->
                onPicked(sorted[which].pkg)
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Очистить") { _, _ ->
                onPicked("")
            }
            .show()
    }
}
