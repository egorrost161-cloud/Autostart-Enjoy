package com.example.autostart

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

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
        return overlay && battery
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
            text = "\nДля работы нужно 2 разрешения.\nНажми на каждое — откроется системная настройка.\nПотом нажми «Обновить статус».\n"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        })

        root.addView(Button(this).apply {
            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this@MainActivity))
                "[OK] Наложение поверх окон" else "[--] Наложение поверх окон"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                } else {
                    Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = "[--] Игнор батареи"
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
            text = "ОБНОВИТЬ СТАТУС"
            setOnClickListener { recreate() }
        })

        root.addView(Button(this).apply {
            text = "ПРОДОЛЖИТЬ"
            setOnClickListener {
                if (areAllPermissionsGranted()) {
                    prefs.edit().putBoolean("setup_done", true).apply()
                }
                recreate()
            }
        })

        setContentView(root)
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
