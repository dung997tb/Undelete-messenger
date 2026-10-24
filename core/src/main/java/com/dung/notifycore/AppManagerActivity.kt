package com.dung.notifycore

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/**
 * Per-app controls: blacklist toggle, chat/regular classification,
 * and the stored-message cap override. Policy defaults (Dung):
 * chat apps unlimited, regular apps 100.
 */
class AppManagerActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var box: LinearLayout

    private val capCycle = listOf<Int?>(null, CoreSettings.UNLIMITED, 100, 500, 1000, 5000)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@AppManagerActivity))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@AppManagerActivity, 14f), Ui.dp(this@AppManagerActivity, 14f), Ui.dp(this@AppManagerActivity, 16f), Ui.dp(this@AppManagerActivity, 8f))
            addView(TextView(this@AppManagerActivity).apply {
                text = "‹"
                textSize = 26f
                setTextColor(Ui.ACCENT)
                setPadding(Ui.dp(this@AppManagerActivity, 4f), 0, Ui.dp(this@AppManagerActivity, 10f), Ui.dp(this@AppManagerActivity, 2f))
                setOnClickListener { finish() }
            })
            addView(TextView(this@AppManagerActivity).apply {
                text = getString(R.string.apps_manage)
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(this@AppManagerActivity))
            })
        }
        root.addView(header)
        val scroll = ScrollView(this)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@AppManagerActivity, 16f), Ui.dp(this@AppManagerActivity, 4f), Ui.dp(this@AppManagerActivity, 16f), Ui.dp(this@AppManagerActivity, 28f))
        }
        scroll.addView(box)
        root.addView(scroll)
        setContentView(root)
        root.setPadding(0, Ui.statusBarHeight(this), 0, 0)
        render()
    }

    private fun capText(pkg: String): String {
        val o = CoreSettings.capOverride(this, pkg)
        val eff = CoreSettings.capFor(this, pkg)
        val effText = if (eff < 0) getString(R.string.cap_unlimited) else getString(R.string.cap_value, eff)
        return if (o == null) getString(R.string.cap_default) + " (" + effText + ")" else effText
    }

    private fun chip(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Ui.ACCENT)
        background = Ui.rounded(Ui.bg(this@AppManagerActivity), 8f, this@AppManagerActivity)
        setPadding(Ui.dp(this@AppManagerActivity, 10f), Ui.dp(this@AppManagerActivity, 6f), Ui.dp(this@AppManagerActivity, 10f), Ui.dp(this@AppManagerActivity, 6f))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun render() {
        box.removeAllViews()
        box.addView(TextView(this).apply {
            text = getString(R.string.blacklist_hint)
            textSize = 11.5f
            setTextColor(Ui.sub(this@AppManagerActivity))
            setPadding(0, 0, 0, Ui.dp(this@AppManagerActivity, 10f))
        })
        val repo = CoreRuntime.repo(this)
        io.execute {
            val pkgs = repo.knownPackages()
            runOnUiThread {
                for (pkg in pkgs) box.addView(appCard(pkg))
            }
        }
    }

    private fun appCard(pkg: String): LinearLayout {
        val ctx = this
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.card(ctx), 12f, ctx)
            setPadding(Ui.dp(ctx, 13f), Ui.dp(ctx, 11f), Ui.dp(ctx, 13f), Ui.dp(ctx, 11f))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = Ui.dp(ctx, 9f)
            layoutParams = lp
        }
        val line1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val icon = Ui.appIcon(packageManager, pkg)
            if (icon != null) {
                addView(ImageView(ctx).apply {
                    setImageDrawable(icon)
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 34f), Ui.dp(ctx, 34f))
                })
            }
            addView(TextView(ctx).apply {
                text = Ui.appLabel(packageManager, pkg)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(ctx))
                setPadding(Ui.dp(ctx, 9f), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                maxLines = 1
            })
            addView(CheckBox(ctx).apply {
                text = getString(R.string.block_label)
                textSize = 12.5f
                setTextColor(Ui.text(ctx))
                isChecked = CoreSettings.isBlacklisted(ctx, pkg)
                setOnCheckedChangeListener { _, checked ->
                    val cur = HashSet(CoreSettings.blacklist(ctx))
                    if (checked) cur.add(pkg) else cur.remove(pkg)
                    CoreSettings.setBlacklist(ctx, cur)
                }
            })
        }
        card.addView(line1)
        val linePin = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Ui.dp(ctx, 7f), 0, 0)
            addView(CheckBox(ctx).apply {
                text = getString(R.string.pin_app_label)
                textSize = 12.5f
                setTextColor(Ui.text(ctx))
                isChecked = CoreSettings.isAppPinned(ctx, pkg)
                setOnCheckedChangeListener { _, checked -> CoreSettings.setAppPinned(ctx, pkg, checked) }
            })
        }
        card.addView(linePin)
        val line2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Ui.dp(ctx, 9f), 0, 0)
            val typeChip = chip(
                getString(
                    R.string.type_label,
                    getString(if (CoreSettings.isChatApp(ctx, pkg)) R.string.app_type_chat else R.string.app_type_normal)
                )
            ) {
                CoreSettings.setChatApp(ctx, pkg, !CoreSettings.isChatApp(ctx, pkg))
                refresh()
            }
            addView(typeChip)
            val capChip = chip(getString(R.string.cap_label, capText(pkg))) {
                val cur = CoreSettings.capOverride(ctx, pkg)
                val idx = capCycle.indexOf(cur)
                val next = capCycle[(idx + 1) % capCycle.size]
                CoreSettings.setCapOverride(ctx, pkg, next)
                val repo = CoreRuntime.repo(ctx)
                io.execute { repo.pruneMessages { p2 -> CoreSettings.capFor(ctx, p2) } }
                refresh()
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = Ui.dp(ctx, 8f)
            capChip.layoutParams = lp
            addView(capChip)
        }
        card.addView(line2)
        return card
    }

    private fun refresh() {
        box.removeAllViews()
        render()
    }
}
