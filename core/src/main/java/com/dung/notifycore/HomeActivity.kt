package com.dung.notifycore

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.dung.notifycore.data.AppSummary
import java.util.concurrent.Executors

/** Unseen-style home: All Messages card + one row per app. */
class HomeActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var listBox: LinearLayout
    private lateinit var allSub: TextView
    private lateinit var grantCard: LinearLayout
    private lateinit var lastLine: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@HomeActivity))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.setPadding(0, Ui.statusBarHeight(this), 0, 0)

        // header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@HomeActivity, 20f), Ui.dp(this@HomeActivity, 18f), Ui.dp(this@HomeActivity, 14f), Ui.dp(this@HomeActivity, 6f))
        }
        header.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.text(this@HomeActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "⚙"
            textSize = 21f
            setTextColor(Ui.ACCENT)
            setPadding(Ui.dp(this@HomeActivity, 10f), Ui.dp(this@HomeActivity, 4f), Ui.dp(this@HomeActivity, 10f), Ui.dp(this@HomeActivity, 4f))
            setOnClickListener { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
        })
        root.addView(header)

        // grant warning card (hidden when granted)
        grantCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.warnBg(this@HomeActivity), 14f, this@HomeActivity)
            setPadding(Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 14f), Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 14f))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 8f), Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 4f))
            layoutParams = lp
            addView(TextView(this@HomeActivity).apply {
                text = getString(R.string.grant_card_title)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(this@HomeActivity))
            })
            addView(TextView(this@HomeActivity).apply {
                text = getString(R.string.grant_card_body)
                textSize = 13f
                setTextColor(Ui.sub(this@HomeActivity))
            })
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        root.addView(grantCard)

        // All Messages card
        val allCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.card(this@HomeActivity), 14f, this@HomeActivity)
            setPadding(Ui.dp(this@HomeActivity, 14f), Ui.dp(this@HomeActivity, 13f), Ui.dp(this@HomeActivity, 14f), Ui.dp(this@HomeActivity, 13f))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 10f), Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 6f))
            layoutParams = lp
            addView(TextView(this@HomeActivity).apply {
                text = "✉"
                textSize = 17f
                setTextColor(Color_white)
                gravity = Gravity.CENTER
                background = Ui.rounded(Ui.ACCENT, 11f, this@HomeActivity)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(this@HomeActivity, 42f), Ui.dp(this@HomeActivity, 42f))
            })
            val mid = LinearLayout(this@HomeActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(this@HomeActivity, 12f), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@HomeActivity).apply {
                    text = getString(R.string.all_messages)
                    textSize = 15.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Ui.text(this@HomeActivity))
                })
                allSub = TextView(this@HomeActivity).apply {
                    textSize = 12.5f
                    setTextColor(Ui.sub(this@HomeActivity))
                }
                addView(allSub)
            }
            addView(mid)
            addView(TextView(this@HomeActivity).apply {
                text = "›"
                textSize = 20f
                setTextColor(Ui.sub(this@HomeActivity))
            })
            setOnClickListener { MessagesActivity.startAll(this@HomeActivity) }
        }
        root.addView(allCard)

        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 6f), Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 24f))
        }
        val searchCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(Ui.card(this@HomeActivity), 14f, this@HomeActivity)
            setPadding(Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 13f), Ui.dp(this@HomeActivity, 16f), Ui.dp(this@HomeActivity, 13f))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = Ui.dp(this@HomeActivity, 16f)
            lp.rightMargin = Ui.dp(this@HomeActivity, 16f)
            lp.bottomMargin = Ui.dp(this@HomeActivity, 10f)
            layoutParams = lp
            isClickable = true
            setOnClickListener { MessagesActivity.startSearch(this@HomeActivity) }
            addView(TextView(this@HomeActivity).apply { text = "\uD83D\uDD0D"; textSize = 16f })
            addView(TextView(this@HomeActivity).apply {
                text = getString(R.string.home_search)
                textSize = 14.5f
                setTextColor(Ui.sub(this@HomeActivity))
                setPadding(Ui.dp(this@HomeActivity, 10f), 0, 0, 0)
            })
        }
        root.addView(searchCard)
        lastLine = TextView(this).apply {
            textSize = 11.5f
            setTextColor(Ui.sub(this@HomeActivity))
            setPadding(Ui.dp(this@HomeActivity, 18f), 0, Ui.dp(this@HomeActivity, 18f), Ui.dp(this@HomeActivity, 8f))
            visibility = View.GONE
        }
        root.addView(lastLine)
        root.addView(listBox)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun isGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun refresh() {
        grantCard.visibility = if (isGranted()) View.GONE else View.VISIBLE
        val repo = CoreRuntime.repo(this)
        io.execute {
            if (SmsSync.hasReadPermission(this@HomeActivity)) SmsSync.syncRecent(this@HomeActivity, repo)
            val msgs = repo.appSummaries()
            val notifs = repo.latestNotificationsByApp()
            val total = repo.messageCount()
            val lastCap = repo.lastCaptureAt()
            runOnUiThread {
                render(msgs, notifs, total)
                if (lastCap != null) {
                    lastLine.text = getString(R.string.home_last_capture) + " \u00B7 " + Ui.fmtWhen(lastCap)
                    lastLine.visibility = View.VISIBLE
                } else {
                    lastLine.visibility = View.GONE
                }
            }
        }
    }

    private fun render(msgs: List<AppSummary>, notifs: List<AppSummary>, total: Long) {
        allSub.text = getString(R.string.saved_count, total)
        listBox.removeAllViews()
        val seen = HashSet<String>()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.card(this@HomeActivity), 14f, this@HomeActivity)
        }
        var rows = 0
        val msgPkgs = msgs.map { it.packageName }.toHashSet()
        val combined = msgs + notifs.filter { it.packageName !in msgPkgs }
        val sorted = combined.sortedWith(
            compareByDescending<AppSummary> { CoreSettings.isAppPinned(this@HomeActivity, it.packageName) }
                .thenByDescending { it.latestTime }
        )
        for (s in sorted) {
            card.addView(appRow(s, fromMessages = s.packageName in msgPkgs))
            rows++
        }
        if (rows == 0) {
            listBox.addView(TextView(this).apply {
                text = getString(R.string.no_messages_yet)
                textSize = 13.5f
                setTextColor(Ui.sub(this@HomeActivity))
                setPadding(0, Ui.dp(this@HomeActivity, 18f), 0, 0)
            })
        } else {
            listBox.addView(card)
            listBox.addView(TextView(this).apply {
                text = getString(R.string.hint_pin_app)
                textSize = 11.5f
                setTextColor(Ui.sub(this@HomeActivity))
                setPadding(Ui.dp(this@HomeActivity, 4f), Ui.dp(this@HomeActivity, 8f), Ui.dp(this@HomeActivity, 4f), 0)
            })
        }
    }

    private fun appRow(s: AppSummary, fromMessages: Boolean): View {
        val ctx = this
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 13f), Ui.dp(ctx, 11f), Ui.dp(ctx, 13f), Ui.dp(ctx, 11f))
            isClickable = true
            setOnClickListener { MessagesActivity.startApp(ctx, s.packageName) }
            setOnLongClickListener {
                CoreSettings.setAppPinned(ctx, s.packageName, !CoreSettings.isAppPinned(ctx, s.packageName))
                refresh()
                true
            }
        }
        // icon or letter avatar
        val icon = Ui.appIcon(packageManager, s.packageName)
        if (icon != null) {
            row.addView(ImageView(this).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 42f), Ui.dp(ctx, 42f))
            })
        } else {
            row.addView(TextView(this).apply {
                text = Ui.appLabel(packageManager, s.packageName).take(1).uppercase()
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color_white)
                gravity = Gravity.CENTER
                background = Ui.rounded(Ui.avatarColor(s.packageName), 11f, ctx)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 42f), Ui.dp(ctx, 42f))
            })
        }
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 11f), 0, Ui.dp(ctx, 8f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(ctx).apply {
                text = (if (CoreSettings.isAppPinned(ctx, s.packageName)) "\uD83D\uDCCC " else "") + Ui.appLabel(packageManager, s.packageName)
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(ctx))
                maxLines = 1
            })
            addView(TextView(ctx).apply {
                text = s.latestText ?: ""
                textSize = 12.5f
                maxLines = 1
                if (fromMessages && s.latestVanished) setTextColor(Ui.RED_DARK) else setTextColor(Ui.sub(ctx))
            })
        }
        row.addView(mid)
        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 88f), ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(TextView(ctx).apply {
                text = Ui.fmtWhen(s.latestTime)
                textSize = 11f
                setTextColor(Ui.sub(ctx))
                gravity = Gravity.END
            })
            if (fromMessages && s.latestVanished) {
                addView(TextView(ctx).apply {
                    text = getString(R.string.chip_recalled)
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color_white)
                    background = Ui.rounded(Ui.RED, 6f, ctx)
                    setPadding(Ui.dp(ctx, 6f), Ui.dp(ctx, 2f), Ui.dp(ctx, 6f), Ui.dp(ctx, 2f))
                    val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp2.topMargin = Ui.dp(ctx, 3f)
                    layoutParams = lp2
                })
            } else {
                addView(TextView(ctx).apply {
                    text = Ui.fmtCount(s.messageCount.toLong())
                    textSize = 10.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color_white)
                    gravity = Gravity.CENTER
                    background = Ui.rounded(Ui.ACCENT, 10f, ctx)
                    setPadding(Ui.dp(ctx, 6f), Ui.dp(ctx, 1f), Ui.dp(ctx, 6f), Ui.dp(ctx, 1f))
                    val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp2.topMargin = Ui.dp(ctx, 3f)
                    layoutParams = lp2
                })
            }
        }
        row.addView(meta)
        return row
    }

    companion object {
        private val Color_white: Int = android.graphics.Color.WHITE
    }
}
