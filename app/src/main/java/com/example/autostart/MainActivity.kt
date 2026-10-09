package com.example.autostart

import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
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
    private var showingSetup = false
    private var lastPermissionState = ""

    // Цвета
    private val colorBg = Color.parseColor("#F5F5F7")
    private val colorCard = Color.WHITE
    private val colorGreen = Color.parseColor("#4CAF50")
    private val colorRed = Color.parseColor("#E53935")
    private val colorGray = Color.parseColor("#EEEEEE")
    private val colorGrayDark = Color.parseColor("#BDBDBD")
    private val colorText = Color.parseColor("#212121")
    private val colorTextLight = Color.parseColor("#616161")
    private val colorBorder = Color.parseColor("#E0E0E0")

    // Для восстановления позиции скролла
    private var savedScrollY: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        LogWriter.init(this)

        if (!areAllPermissionsGranted()) {
            showingSetup = true
            showSetupScreen()
        } else {
            showingSetup = false
            showMainScreen()
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

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(colorTextLight)
            letterSpacing = 0.1f
            setPadding(10, 30, 10, 10)
        }
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(colorText)
            setPadding(0, 12, 0, 4)
        }
    }

    private fun hint(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(colorTextLight)
            setPadding(0, 4, 0, 8)
        }
    }

    private fun inputField(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            textSize = 14f
            setTextColor(colorText)
            setPadding(24, 20, 24, 20)
            background = roundedBgWithBorder(colorBg, colorBorder, 20)
        }
    }

    private fun roundedButton(text: String, bgColor: Int, textColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
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

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun overlayGranted(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)
    }

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

    private fun permissionButton(title: String, granted: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 40, 30, 40)
            setBackgroundColor(colorBg)
        }

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
        val bgDelayInput = inputField("1.5").apply {
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
        }
        delayCard.addView(bgDelayInput)
        delayCard.addView(hint("Диапазон: 0.5 – 10 сек"))
        root.addView(delayCard)

        // ============ КАРТОЧКА: АВТОПЛЕЙ ============
        val autoplayCard = card()
        val autoplayCheck = CheckBox(this).apply {
            text = "Автоплей Яндекс.Музыки"
            textSize = 15f
            setTextColor(colorText)
            isChecked = prefs.getBoolean("autoplay_yandex", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("autoplay_yandex", checked).apply()
            }
        }
        autoplayCard.addView(autoplayCheck)
        autoplayCard.addView(hint("Пробует отправить команду Play через 3 и 6 секунд после запуска"))
        root.addView(autoplayCard)

        // ============ КАРТОЧКА: МОНИТОР (СО СПОЙЛЕРОМ) ============
        val monitorCard = card()

        // Спойлер-заголовок
        val monitorOpen = prefs.getBoolean("monitor_spoiler_open", false)
        val spoilerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val newState = !prefs.getBoolean("monitor_spoiler_open", false)
                prefs.edit().putBoolean("monitor_spoiler_open", newState).apply()
                recreate()
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
            // Содержимое монитора
            val monitorOn = prefs.getBoolean("monitor_enabled", false)

            val monitorEnabledCheck = CheckBox(this).apply {
                text = "Включить монитор"
                textSize = 15f
                setTextColor(colorText)
                isChecked = monitorOn
                setPadding(0, 20, 0, 0)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("monitor_enabled", checked).apply()
                    recreate()
                }
            }
            monitorCard.addView(monitorEnabledCheck)

            // Карточка-кнопка выбора пакета
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
                    val label = packageManager.getApplicationLabel(appInfo).toString()
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
                        text = label
                        textSize = 16f
                        setTextColor(colorText)
                    })
                    textCol.addView(TextView(this).apply {
                        text = savedMonitorPkg
                        textSize = 11f
                        setTextColor(colorTextLight)
                    })
                    packageCard.addView(textCol)

                    val clearBtn = TextView(this).apply {
                        text = "✕"
                        textSize = 26f
                        setTextColor(colorRed)
                        gravity = Gravity.CENTER
                        setPadding(20, 10, 20, 10)
                        setOnClickListener {
                            prefs.edit().remove("monitor_package").apply()
                            Toast.makeText(this@MainActivity, "Пакет монитора очищен", Toast.LENGTH_SHORT).show()
                            recreate()
                        }
                    }
                    packageCard.addView(clearBtn)
                } else {
                    packageCard.addView(TextView(this).apply {
                        text = "⚠  Приложение удалено — нажми, чтобы выбрать заново"
                        textSize = 14f
                        setTextColor(colorRed)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val clearBtn = TextView(this).apply {
                        text = "✕"
                        textSize = 26f
                        setTextColor(colorRed)
                        gravity = Gravity.CENTER
                        setPadding(20, 10, 20, 10)
                        setOnClickListener {
                            prefs.edit().remove("monitor_package").apply()
                            recreate()
                        }
                    }
                    packageCard.addView(clearBtn)
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
                    recreate()
                }
            }
            monitorCard.addView(packageCard)

            monitorCard.addView(label("Интервал проверки (сек)"))
            val intervalInput = inputField("30").apply {
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
            }
            monitorCard.addView(intervalInput)
            monitorCard.addView(hint("5 – 300 сек"))

            monitorCard.addView(label("Неактивность порог (мин)"))
            val idleInput = inputField("30").apply {
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
            }
            monitorCard.addView(idleInput)
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
            recreate()
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
        // Якорь для прокрутки к списку
        val listAnchorId = View.generateViewId()
        val listTitle = TextView(this).apply {
            id = listAnchorId
            text = "ВЫБЕРИТЕ ПРИЛОЖЕНИЯ"
            textSize = 13f
            setTextColor(colorTextLight)
            letterSpacing = 0.1f
            setPadding(10, 30, 10, 10)
        }
        root.addView(listTitle)

        root.addView(TextView(this).apply {
            text = "Тап по строке — выбор. Фон — свернуть после запуска."
            textSize = 12f
            setTextColor(colorTextLight)
            setPadding(10, 0, 10, 10)
        })

        val pm = packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        val savedPackages = prefs.getStringSet("target_packages", emptySet()) ?: emptySet()

        val launchableApps = apps.filter { app ->
            app.packageName != packageName &&
            pm.getLaunchIntentForPackage(app.packageName) != null
        }

        val sortedApps = launchableApps.sortedWith(
            compareByDescending<android.content.pm.ApplicationInfo> {
                savedPackages.contains(it.packageName)
            }.thenBy {
                pm.getApplicationLabel(it).toString().lowercase()
            }
        )

        for (app in sortedApps) {
            val label = pm.getApplicationLabel(app).toString()
            val icon = pm.getApplicationIcon(app)
            val isSelected = savedPackages.contains(app.packageName)
            root.addView(createAppRow(label, icon, isSelected, app.packageName))
        }

        // Оборачиваем в ScrollView и восстанавливаем позицию скролла
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(colorBg)
            addView(root)
        }
        setContentView(scrollView)

        // Восстанавливаем позицию скролла и/или прокручиваем к списку
        scrollView.post {
            val scrollToList = intent.getBooleanExtra("SCROLL_TO_LIST", false)
            val scrollToListIndex = intent.getIntExtra("SCROLL_TO_LIST_INDEX", -1)

            if (scrollToList && scrollToListIndex >= 0) {
                // Ищем View строки, которую только что нажали, и скроллим к ней
                // Проще: скроллим так, чтобы строка оказалась на видном месте
                val listTitleView = scrollView.findViewById<View>(listAnchorId)
                if (listTitleView != null) {
                    scrollView.smoothScrollTo(0, listTitleView.top)
                }
            } else if (savedScrollY > 0) {
                scrollView.scrollTo(0, savedScrollY)
            }
        }
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
        val pm = packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)

        val launchableApps = apps.filter { app ->
            app.packageName != packageName &&
            pm.getLaunchIntentForPackage(app.packageName) != null
        }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val labels = launchableApps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите приложение для монитора")
            .setItems(labels) { _, which ->
                val picked = launchableApps[which].packageName
                onPicked(picked)
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Очистить") { _, _ ->
                onPicked("")
            }
            .show()
    }

    // ============ СТРОКА ПРИЛОЖЕНИЯ ============

    private fun formatSeconds(sec: Float): String {
        return if (sec % 1.0f == 0.0f) sec.toInt().toString() else sec.toString()
    }

    private fun createAppRow(label: String, icon: Drawable, isSelected: Boolean, pkg: String): LinearLayout {
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
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(90, 90).apply { setMargins(0, 0, 20, 0) }
        })

        row.addView(TextView(this).apply {
            text = if (isSelected) "✓  $label" else "○  $label"
            textSize = 16f
            setTextColor(if (isSelected) Color.WHITE else colorText)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val delayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getLong("delay_$pkg", 15L).toString())
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
                        prefs.edit().putLong("delay_$pkg", delay).apply()
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
            isChecked = prefs.getBoolean("bg_$pkg", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("bg_$pkg", checked).apply()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 0, 0) }
        }
        row.addView(bgCheck)

        row.setOnClickListener {
            val currentSet = prefs.getStringSet("target_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (currentSet.contains(pkg)) {
                currentSet.remove(pkg)
                Toast.makeText(this@MainActivity, "Автозапуск отключён", Toast.LENGTH_SHORT).show()
            } else {
                currentSet.add(pkg)
                Toast.makeText(this@MainActivity, "Выбрано: $label", Toast.LENGTH_SHORT).show()
            }
            prefs.edit().putStringSet("target_packages", currentSet).apply()

            // Вместо recreate() с полным сбросом скролла — recreate() с сохранением
            // позиции через флаг SCROLL_TO_LIST, чтобы вернуться к списку приложений
            val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("SCROLL_TO_LIST", true)
                putExtra("SCROLL_TO_LIST_INDEX", 0)
            }
            startActivity(intent)
            finish()
        }

        return row
    }
}
