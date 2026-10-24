package com.dung.notifycore

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.Executors

/**
 * First-run onboarding (1.3.3, modeled on Unseen Messenger's flow;
 * step 3 added in 1.3.4):
 * Step 1 — notification access (the app's core permission).
 * Step 2 — SMS permission (optional; skippable, also available in Settings).
 * Step 3 — battery-optimization exemption (optional; skippable). On Xiaomi
 *          the primary button opens MIUI's Autostart screen instead (MIUI
 *          kills background apps independently of the Android whitelist),
 *          with a secondary link to the App info page for its Battery saver.
 * Each step flips to a success state once its permission is held.
 * Shown until CoreSettings.onboardingDone is set; HomeActivity redirects here.
 */
class OnboardingActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private var step = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@OnboardingActivity))
        }
        root.setPadding(
            Ui.dp(this, 24f),
            Ui.statusBarHeight(this) + Ui.dp(this, 18f),
            Ui.dp(this, 24f),
            Ui.dp(this, 24f)
        )
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    private fun listenerGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun battGranted(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName)

    private fun isXiaomi(): Boolean =
        Build.MANUFACTURER.lowercase().contains("xiaomi") ||
            Build.BRAND.lowercase().contains("xiaomi") ||
            Build.BRAND.lowercase().contains("redmi") ||
            Build.BRAND.lowercase().contains("poco")

    private fun grantedNow(): Boolean = when (step) {
        1 -> listenerGranted()
        2 -> SmsSync.hasReadPermission(this)
        else -> battGranted()
    }

    private fun render() {
        val ctx = this
        root.removeAllViews()
        root.addView(TextView(ctx).apply {
            text = getString(
                when (step) {
                    1 -> R.string.ob_step1
                    2 -> R.string.ob_step2
                    else -> R.string.ob_step3
                }
            )
            textSize = 16f
            setTextColor(Ui.text(ctx))
        })

        val mid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(mid)

        val granted = grantedNow()
        val emoji = when {
            granted -> "👍"
            step == 1 -> "🔔"
            step == 2 -> "💬"
            else -> "🔋"
        }
        mid.addView(TextView(ctx).apply {
            text = emoji
            textSize = 76f
            gravity = Gravity.CENTER
        })
        val title = when {
            granted -> R.string.ob_great
            step == 1 -> R.string.ob_notif_title
            step == 2 -> R.string.ob_sms_title
            else -> R.string.ob_batt_title
        }
        mid.addView(TextView(ctx).apply {
            text = getString(title)
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.text(ctx))
            setPadding(0, Ui.dp(ctx, 28f), 0, 0)
        })
        val desc = when {
            granted && step == 1 -> R.string.ob_notif_done
            granted && step == 2 -> R.string.ob_sms_done
            granted -> R.string.ob_batt_done
            step == 1 -> R.string.ob_notif_desc
            step == 2 -> R.string.ob_sms_desc
            else -> R.string.ob_batt_desc
        }
        mid.addView(TextView(ctx).apply {
            text = getString(desc)
            textSize = 14.5f
            setTextColor(Ui.sub(ctx))
            setPadding(0, Ui.dp(ctx, 12f), 0, 0)
            setLineSpacing(0f, 1.25f)
        })

        if (step == 3 && !granted && isXiaomi()) {
            root.addView(TextView(ctx).apply {
                text = getString(R.string.ob_batt_details)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.ACCENT)
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(ctx, 14f), 0, Ui.dp(ctx, 2f))
                setOnClickListener {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            })
        }
        if (step >= 2 && !granted) {
            root.addView(TextView(ctx).apply {
                text = getString(R.string.ob_skip)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.ACCENT)
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(ctx, 14f), 0, Ui.dp(ctx, 14f))
                setOnClickListener { onSkip() }
            })
        }
        root.addView(Button(ctx).apply {
            text = getString(if (granted) R.string.ob_continue else R.string.ob_enable)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = Ui.rounded(Ui.ACCENT, 12f, ctx)
            setPadding(0, Ui.dp(ctx, 15f), 0, Ui.dp(ctx, 15f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onPrimary(granted) }
        })
    }

    private fun onSkip() {
        if (step == 2) {
            step = 3
            render()
        } else {
            finishOnboarding()
        }
    }

    private fun onPrimary(granted: Boolean) {
        when {
            step == 1 && !granted ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            step == 1 -> {
                step = 2
                render()
            }
            step == 2 && !granted -> requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_SMS,
                    android.Manifest.permission.RECEIVE_SMS
                ),
                77
            )
            step == 2 -> {
                step = 3
                render()
            }
            !granted -> openBatterySettings()
            else -> finishOnboarding()
        }
    }

    private fun openBatterySettings() {
        if (isXiaomi()) {
            try {
                startActivity(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                )
                return
            } catch (e: Exception) {
                // MIUI screen moved/absent — fall through to the standard flow.
            }
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77 && SmsSync.hasReadPermission(this)) {
            val repo = CoreRuntime.repo(this)
            io.execute { SmsSync.syncFull(this@OnboardingActivity, repo) }
        }
        render()
    }

    private fun finishOnboarding() {
        CoreSettings.setOnboardingDone(this, true)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
