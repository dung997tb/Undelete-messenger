package com.dung.notifycore

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@SettingsActivity))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@SettingsActivity, 14f), Ui.dp(this@SettingsActivity, 14f), Ui.dp(this@SettingsActivity, 16f), Ui.dp(this@SettingsActivity, 8f))
            addView(TextView(this@SettingsActivity).apply {
                text = "‹"
                textSize = 26f
                setTextColor(Ui.ACCENT)
                setPadding(Ui.dp(this@SettingsActivity, 4f), 0, Ui.dp(this@SettingsActivity, 10f), Ui.dp(this@SettingsActivity, 2f))
                setOnClickListener { finish() }
            })
            addView(TextView(this@SettingsActivity).apply {
                text = getString(R.string.settings)
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(this@SettingsActivity))
            })
        }
        root.addView(header)
        val scroll = ScrollView(this)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@SettingsActivity, 16f), Ui.dp(this@SettingsActivity, 4f), Ui.dp(this@SettingsActivity, 16f), Ui.dp(this@SettingsActivity, 28f))
        }
        scroll.addView(box)
        root.addView(scroll)
        setContentView(root)
        root.setPadding(0, Ui.statusBarHeight(this), 0, 0)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun granted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        setTextColor(Ui.sub(this@SettingsActivity))
        setPadding(Ui.dp(this@SettingsActivity, 4f), Ui.dp(this@SettingsActivity, 14f), 0, Ui.dp(this@SettingsActivity, 6f))
    }

    private fun groupCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.rounded(Ui.card(this@SettingsActivity), 12f, this@SettingsActivity)
    }

    private fun row(label: String, value: String?, red: Boolean = false, chevron: Boolean = false, onClick: (() -> Unit)? = null): LinearLayout {
        val ctx = this
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 14f), Ui.dp(ctx, 12f), Ui.dp(ctx, 14f), Ui.dp(ctx, 12f))
            addView(TextView(ctx).apply {
                text = label
                textSize = 14f
                setTextColor(if (red) Ui.RED else Ui.text(ctx))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (value != null) {
                addView(TextView(ctx).apply {
                    text = value
                    textSize = 13f
                    setTextColor(Ui.sub(ctx))
                })
            }
            if (chevron) {
                addView(TextView(ctx).apply {
                    text = " ›"
                    textSize = 15f
                    setTextColor(Ui.sub(ctx))
                })
            }
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
        }
    }

    private fun render() {
        box.removeAllViews()
        val repo = CoreRuntime.repo(this)

        box.addView(sectionLabel(getString(R.string.sec_core_status)))
        val g1 = groupCard()
        g1.addView(row(getString(R.string.notif_access), if (granted()) getString(R.string.granted) else getString(R.string.not_granted)))
        g1.addView(row(getString(R.string.listener), if (CoreStatus.isConnected(this)) getString(R.string.running) else getString(R.string.stopped)))
        val savedRow = row(getString(R.string.saved_label), "…")
        g1.addView(savedRow)
        g1.addView(row(getString(R.string.open_notif_settings), null, chevron = true) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        val smsGranted = SmsSync.hasReadPermission(this)
        g1.addView(row(
            getString(R.string.sms_perm_label),
            if (smsGranted) getString(R.string.granted) else getString(R.string.not_granted),
            chevron = !smsGranted
        ) {
            if (!SmsSync.hasReadPermission(this@SettingsActivity)) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_SMS,
                        android.Manifest.permission.RECEIVE_SMS
                    ),
                    77
                )
            }
        })
        box.addView(g1)
        box.addView(TextView(this).apply {
            text = getString(R.string.restricted_hint)
            textSize = 11.5f
            setTextColor(Ui.sub(this@SettingsActivity))
            setPadding(Ui.dp(this@SettingsActivity, 4f), Ui.dp(this@SettingsActivity, 7f), Ui.dp(this@SettingsActivity, 4f), 0)
        })
        io.execute {
            val n = repo.messageCount()
            runOnUiThread {
                (savedRow.getChildAt(1) as? TextView)?.text =
                    getString(R.string.saved_count, n)
            }
        }

        box.addView(sectionLabel(getString(R.string.sec_storage)))
        val g2 = groupCard()
        g2.addView(row(getString(R.string.policy_summary), null))
        g2.addView(row(getString(R.string.apps_manage), null, chevron = true) {
            startActivity(Intent(this, AppManagerActivity::class.java))
        })
        g2.addView(row(getString(R.string.clear_all), null, red = true) {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_confirm_title)
                .setMessage(R.string.clear_confirm_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    io.execute { repo.deleteAll(); Avatars.clearAll(this@SettingsActivity); runOnUiThread { render() } }
                }
                .show()
        })
        box.addView(g2)

        box.addView(sectionLabel(getString(R.string.sec_backup)))
        val g4 = groupCard()
        g4.addView(row(getString(R.string.export_backup), null, chevron = true) {
            startActivity(Intent(this, BackupPickerActivity::class.java))
        })
        g4.addView(row(getString(R.string.import_backup), null, chevron = true) {
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/json"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(pick, 55)
        })
        box.addView(g4)

        box.addView(sectionLabel(getString(R.string.sec_appearance)))
        val g3 = groupCard()
        val modeNames = arrayOf(R.string.mode_system, R.string.mode_light, R.string.mode_dark)
        g3.addView(row(getString(R.string.dark_mode), getString(modeNames[CoreSettings.darkMode(this)]), chevron = true) {
            val next = (CoreSettings.darkMode(this) + 1) % 3
            CoreSettings.setDarkMode(this, next)
            when (next) {
                1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            render()
        })
        g3.addView(row(getString(R.string.language), getString(R.string.language_value)))
        box.addView(g3)

        box.addView(TextView(this).apply {
            text = getString(R.string.privacy_note)
            textSize = 11.5f
            setTextColor(Ui.sub(this@SettingsActivity))
            setPadding(Ui.dp(this@SettingsActivity, 4f), Ui.dp(this@SettingsActivity, 14f), Ui.dp(this@SettingsActivity, 4f), 0)
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) {
            render()
            if (SmsSync.hasReadPermission(this)) {
                val repo = CoreRuntime.repo(this)
                io.execute { SmsSync.syncFull(this@SettingsActivity, repo) }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 55 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val repo = CoreRuntime.repo(this)
            io.execute {
                var counts: Pair<Int, Int>? = null
                try {
                    contentResolver.openInputStream(uri)?.use { ins ->
                        val jr = android.util.JsonReader(
                            java.io.InputStreamReader(ins, "UTF-8")
                        )
                        counts = repo.readBackup(jr)
                        jr.close()
                    }
                } catch (e: Exception) {
                    counts = null
                }
                val c = counts
                runOnUiThread {
                    if (c != null) {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle(R.string.import_done_title)
                            .setMessage(getString(R.string.import_done_msg, c.first, c.second))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        render()
                    }
                }
            }
        }
    }
}
