package com.example.autostart

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    private val REQUEST_NOTIF = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        LogWriter.init(this)

        if (!areAllPermissionsGranted()) {
            showSetupScreen()
        } else {
            showMainScreen()
        }
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

    private fun overlayLabel(): String {
        val granted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)
        return if (granted) "[OK] Наложение поверх окон" else "[--] Наложение поверх окон"
    }

    private fun usageStatsLabel(): String {
        return if (hasUsageStatsPermission()) "[OK] Статистика использования" else "[--] Статистика использования"
    }

    private fun batteryLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "[OK] Игнор батареи"
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return if (pm.isIgnoringBatteryOptimizations(packageName)) "[OK] Игнор батареи" else "[--] Игнор батареи"
    }

    private fun notificationsLabel(): String {
        return if (areNotificationsEnabled()) "[OK] Уведомления" else "[--] Уведомления"
    }

    private fun showSetupScreen() {
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
            text = "\nДля работы нужно 4 разрешения.\nНажми на каждое — откроется системная настройка.\nПотом нажми «Обновить статус».\n"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        })

        root.addView(Button(this).apply {
            text = overlayLabel()
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                } else {
                    Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = usageStatsLabel()
            setOnClickListener {
                if (!hasUsageStatsPermission()) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    Toast.makeText(this@MainActivity, "Найди AutoStart Pro и включи доступ", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = batteryLabel()
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = android.net.Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        root.addView(Button(this).apply {
            text = notificationsLabel()
            setOnClickListener {
                if (!areNotificationsEnabled()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), REQUEST_NOTIF)
                    } else {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        startActivity(intent)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "ОБНОВИТЬ СТАТУС"
            setOnClickListener { recreate() }
        })

        root.addView(Button(this).apply {
            text = "ПРОДОЛЖИТЬ"
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIF) {
            recreate()
        }
    }

    private fun showMainScreen() {
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
            setOnClickListener {
                val intent = Intent(this@MainActivity, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
                Toast.makeText(this@MainActivity, "Сервис запущен", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Отключить автозапуск"
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

        val scrollView = ScrollView(this)
        val listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val pm = packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        val savedPackages = prefs.getStringSet("target_packages", emptySet()) ?: emptySet()

        val sortedApps = apps.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        for (app in sortedApps) {
            if (app.packageName == packageName) continue
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue

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
            setBackgroundColor(if (isSelected) 0xFF4CAF50.toInt() else 0xFFEEEEEE.toInt())
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
            text = if (isSelected) "[OK] $label" else label
            textSize = 16f
            setTextColor(0xFF222222.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val delayInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            val savedDelay = prefs.getLong("delay_$pkg", 15L)
            setText(savedDelay.toString())
            textSize = 14f
            width = 120
            hint = "15"
            setPadding(10, 10, 10, 10)
        }
        row.addView(delayInput)

        row.setOnClickListener {
            val currentSet = prefs.getStringSet("target_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (currentSet.contains(pkg)) {
                currentSet.remove(pkg)
                Toast.makeText(this, "Автозапуск отключён", Toast.LENGTH_SHORT).show()
            } else {
                currentSet.add(pkg)
                Toast.makeText(this, "Выбрано: $label", Toast.LENGTH_SHORT).show()
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
