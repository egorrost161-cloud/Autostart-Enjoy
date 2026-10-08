package com.example.autostart

import android.app.AlertDialog
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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
    private val colorBoxBorder = Color.parseColor("#BDBDBD")

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
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
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
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            return false
        }
    }

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

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
        try { startActivity(intent) } catch (e: Exception) {
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
            } else Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
        })

        root.addView(permissionButton("Статистика использования", hasUsageStatsPermission()) {
            if (!hasUsageStatsPermission()) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                Toast.makeText(this@MainActivity, "Найди AutoStart Pro и включи доступ", Toast.LENGTH_LONG).show()
            } else Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
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
            } else Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
        })

        root.addView(permissionButton("Уведомления", areNotificationsEnabled()) {
            if (!areNotificationsEnabled()) {
                openNotificationSettings()
                Toast.makeText(this@MainActivity, "Включи уведомления для AutoStart Pro", Toast.LENGTH_LONG).show()
            } else Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
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
                if (areAllPermissionsGranted()) prefs.edit().putBoolean("setup_done", true).apply()
                else Toast.makeText(this@MainActivity, "Не все разрешения выданы", Toast.LENGTH_SHORT).show()
                recreate()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
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

        // === Пауза сворачивания ===
        root.addView(TextView(this).apply {
            text = "Пауза перед сворачиванием (сек):"
            textSize = 14f
            setPadding(0, 10, 0, 0)
        })

        val bgDelayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            val savedSec = prefs.getFloat("bg_go_home_delay_sec", 1.5f)
            setText(formatSeconds(savedSec))
            textSize = 14f
            hint = "1.5"
            setPadding(10, 10, 10, 10)
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
        root.addView(bgDelayInput)

        root.addView(TextView(this).apply {
            text = "Диапазон: 0.5 – 10 сек."
            textSize = 11f
            setPadding(0, 0, 0, 20)
        })

        // ============ БЛОК МОНИТОРА ============
        root.addView(TextView(this).apply {
            text = "─── МОНИТОР ЛАУНЧЕРА ───"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        })

        // Галочка включения монитора
        val monitorEnabledCheck = CheckBox(this).apply {
            text = "Включить монитор"
            textSize = 16f
            isChecked = prefs.getBoolean("monitor_enabled", false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("monitor_enabled", checked).apply()
                recreate()
            }
        }
        root.addView(monitorEnabledCheck)

        val monitorOn = prefs.getBoolean("monitor_enabled", false)

        // Карточка-кнопка выбора пакета монитора
        val packageCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(colorGray)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 10) }
        }

        val savedMonitorPkg = prefs.getString("monitor_package", "") ?: ""
        if (savedMonitorPkg.isNotEmpty()) {
            // Проверяем, установлен ли пакет
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

                val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                textCol.addView(TextView(this).apply {
                    text = label
                    textSize = 16f
                    setTextColor(colorGrayText)
                })
                textCol.addView(TextView(this).apply {
                    text = savedMonitorPkg
                    textSize = 11f
                    setTextColor(Color.GRAY)
                })
                packageCard.addView(textCol)
            } else {
                packageCard.addView(TextView(this).apply {
                    text = "⚠  Приложение удалено — нажми, чтобы выбрать заново"
                    textSize = 14f
                    setTextColor(Color.RED)
                })
            }
        } else {
            packageCard.addView(TextView(this).apply {
                text = "Не выбрано — нажми, чтобы выбрать приложение"
                textSize = 14f
                setTextColor(colorGrayText)
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
        root.addView(packageCard)

        // Поля интервал/неактивность
        root.addView(TextView(this).apply {
            text = "Интервал проверки (сек), 5–300:"
            textSize = 13f
            setPadding(0, 10, 0, 0)
        })
        val intervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getLong("monitor_interval_sec", 30L).toString())
            textSize = 14f
            hint = "30"
            setPadding(10, 10, 10, 10)
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
        root.addView(intervalInput)

        root.addView(TextView(this).apply {
            text = "Неактивность порог (мин), 1–240:"
            textSize = 13f
            setPadding(0, 10, 0, 0)
        })
        val idleInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getLong("monitor_idle_minutes", 30L).toString())
            textSize = 14f
            hint = "30"
            setPadding(10, 10, 10, 10)
            isEnabled = monitorOn
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val v = s?.toString()?.toLongOrNull() ?: return
                    if (v in 1L..240L) prefs.edit().putLong("monitor_idle_minutes", v).apply()
               
