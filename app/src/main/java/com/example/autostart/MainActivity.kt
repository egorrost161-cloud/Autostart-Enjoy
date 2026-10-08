package com.example.autostart

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var showingSetup = false
    private var lastPermissionState = ""

    private val colorGreen = Color.parseColor("#4CAF50")
    private val colorGray = Color.parseColor("#E0E0E0")
    private val colorGreenText = Color.WHITE
    private val colorGrayText = Color.parseColor("#222222")

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

    private fun permissionStateKey(): String {
        return "${Settings.canDrawOverlays(this)}|${hasUsageStatsPermission()}|${batteryGranted()}|${areNotificationsEnabled()}"
    }

    private fun areAllPermissionsGranted(): Boolean {
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
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
            setTextColor(if (granted) colorGreenText else colorGrayText)
            setBackgroundColor(if (granted) colorGreen else colorGray)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
            setOnClickListener { onClick() }
        }
    }

    private fun showSetupScreen() {
        showingSetup = true
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        root.addView(TextView(this).apply {
            text = "AutoStart Pro"
            textSize = 26f
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "\nДля работы нужно 4 разрешения.\nНажми на каждое — откроется системная настройка.\nКогда галочка станет зелёной — жми «ПРОДОЛЖИТЬ».\n"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        })

        root.addView(permissionButton("Наложение поверх окон", overlayGranted()) {
            if (!overlayGranted()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                }
            } else {
                Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(permissionButton("Статистика использования", hasUsageStatsPermission()) {
            if (!hasUsageStatsPermission()) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                Toast.makeText(this@MainActivity, "Найди AutoStart Pro и включи доступ", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(permissionButton("Игнор батареи", batteryGranted()) {
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

        root.addView(permissionButton("Уведомления", areNotificationsEnabled()) {
            if (!areNotificationsEnabled()) {
                openNotificationSettings()
                Toast.makeText(this@MainActivity, "Включи уведомления для AutoStart Pro", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "ОБНОВИТЬ СТАТУС"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 20, 0, 6) }
            setOnClickListener { recreate() }
        })

        root.addView(Button(this).apply {
            text = "ПРОДОЛЖИТЬ"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
            setOnClickListener {
                if (areAllPermissionsGranted()) {
                    prefs.edit().putBoolean("setup_done", true).apply()
                } else {
                    Toast.makeText(this@MainActivity, "Не все разрешения выданы", Toast.LENGTH_SHORT).show()
                }
                recreate()
            }
        })

        setContentView(root)
    }

    private fun showMainScreen() {
        showingSetup = false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        root.addView(TextView(this).apply {
            text = "AutoStart Pro"
            textSize = 24f
        })

        root.addView(Button(this).apply {
            text = "Запустить сервис сейчас"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 6) }
            setOnClickListener {
                val intent = Intent(this@MainActivity, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
                Toast.makeText(this@MainActivity, "Сервис запущен", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Отключить автозапуск"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
            setOnClickListener {
                prefs.edit().remove("target_packages").apply()
                Toast.makeText(this@MainActivity, "Отключено", Toast.LENGTH_SHORT).show()
                recreate()
            }
        })

        root.addView(TextView(this).apply {
            text = "ВЫБЕРИТЕ ПРИЛОЖЕНИЯ"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        })

        root.addView(TextView(this).apply {
            text = "Тап по строке — выбор. Фон — свернуть после запуска."
            textSize = 12f
            setPadding(0, 0, 0, 10)
        })

        val scrollView = ScrollView(this)
        val listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

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
            listLayout.addView(createAppRow(label, icon, isSelected, app.packageName))
        }

        scrollView.addView(listLayout)
        root.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun createAppRow(label: String, icon: Drawable, isSelected: Boolean, pkg: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(if (isSelected) colorGreen else colorGray)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 6, 0, 6) }
        }

        row.addView(ImageView(this).apply {
            setImageDrawable(icon)
            layoutParams = LinearLayout.LayoutParams(100, 100).apply { setMargins(0, 0, 20, 0) }
        })

        row.addView(TextView(this).apply {
            text = if (isSelected) "✓  $label" else "○  $label"
            textSize = 16f
            setTextColor(if (isSelected) colorGreenText else colorGrayText)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Поле задержки
        val delayInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            val savedDelay = prefs.getLong("delay_$pkg", 15L)
            setText(savedDelay.toString())
            textSize = 14f
            width = 120
            hint = "15"
            setPadding(10, 10, 10, 10)
            setTextColor(colorGrayText)
        }
        row.addView(delayInput)

        // Чекбокс «Фон»
        val bgCheck = CheckBox(this).apply {
            text = "Фон"
            textSize = 12f
            setTextColor(if (isSelected) colorGreenText else colorGrayText)
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

        // Клик по строке — выбор/снятие
        row.setOnClickListener {
            val currentSet = prefs.getStringSet("target_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (currentSet.contains(pkg)) {
                currentSet.remove(pkg)
                Toast.makeText(this@MainActivity, "Автозапуск отключён", Toast.LENGTH_SHORT).show()
            } else {
                currentSet.add(pkg)
                Toast.makeText(this@MainActivity, "Выбрано: $label", Toast.LENGTH_SHORT).show()
            }
            val delay = delayInput.text.toString().toLongOrNull() ?: 15L
            prefs.edit()
                .putStringSet("target_packages", currentSet)
                .putLong("delay_$pkg", delay)
                .apply()
            recreate()
        }

        return row
    }
}
