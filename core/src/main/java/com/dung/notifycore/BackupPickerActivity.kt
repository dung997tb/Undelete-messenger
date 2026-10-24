package com.dung.notifycore

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dung.notifycore.data.AppSummary
import java.util.concurrent.Executors

/**
 * Backup picker (1.3.1): choose whole apps or individual conversations
 * to include in the backup file. Default = everything selected.
 * Notifications follow any app that contributes at least one item.
 */
class BackupPickerActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var listBox: LinearLayout
    private lateinit var summaryLine: TextView
    private lateinit var exportBtn: Button

    private var apps: List<AppSummary> = emptyList()
    private var msgPkgs: Set<String> = emptySet()
    private val fullApps = HashSet<String>()
    private val selConvs = HashSet<Pair<String, String>>()
    private val convsCache = HashMap<String, List<String?>>()
    private val expanded = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(ctx))
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 14f), Ui.dp(ctx, 14f), Ui.dp(ctx, 16f), Ui.dp(ctx, 8f))
            addView(TextView(ctx).apply {
                text = "‹"
                textSize = 26f
                setTextColor(Ui.ACCENT)
                setPadding(Ui.dp(ctx, 4f), 0, Ui.dp(ctx, 10f), Ui.dp(ctx, 2f))
                setOnClickListener { finish() }
            })
            addView(TextView(ctx).apply {
                text = getString(R.string.backup_picker_title)
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(ctx))
            })
        })
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(ctx, 16f), Ui.dp(ctx, 2f), Ui.dp(ctx, 16f), Ui.dp(ctx, 2f))
            addView(Button(ctx).apply {
                text = getString(R.string.select_all_apps)
                setOnClickListener {
                    fullApps.clear()
                    for (a in apps) fullApps.add(a.packageName)
                    selConvs.clear()
                    renderList()
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(ctx).apply {
                text = getString(R.string.clear_selection)
                setOnClickListener {
                    fullApps.clear()
                    selConvs.clear()
                    renderList()
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = Ui.dp(ctx, 10f)
                }
            })
        })
        summaryLine = TextView(this).apply {
            textSize = 12f
            setTextColor(Ui.sub(ctx))
            setPadding(Ui.dp(ctx, 20f), Ui.dp(ctx, 4f), Ui.dp(ctx, 20f), Ui.dp(ctx, 2f))
        }
        root.addView(summaryLine)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 16f), Ui.dp(ctx, 6f), Ui.dp(ctx, 16f), Ui.dp(ctx, 16f))
        }
        scroll.addView(listBox)
        root.addView(scroll)
        exportBtn = Button(this).apply {
            text = getString(R.string.backup_selected)
            setOnClickListener { doExport() }
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 16f), Ui.dp(ctx, 8f), Ui.dp(ctx, 16f), Ui.dp(ctx, 14f))
            addView(exportBtn)
        })
        setContentView(root)
        root.setPadding(0, Ui.statusBarHeight(this), 0, 0)
        load()
    }

    private fun load() {
        val repo = CoreRuntime.repo(this)
        io.execute {
            val msgs = repo.appSummaries()
            val mp = msgs.map { it.packageName }.toHashSet()
            val notifs = repo.latestNotificationsByApp().filter { it.packageName !in mp }
            val all = (msgs + notifs).sortedByDescending { it.latestTime }
            runOnUiThread {
                apps = all
                msgPkgs = mp
                fullApps.clear()
                for (a in all) fullApps.add(a.packageName)
                renderList()
            }
        }
    }

    private fun deferRender() {
        listBox.post { renderList() }
    }

    private fun convSelected(pkg: String, conv: String?): Boolean =
        fullApps.contains(pkg) || selConvs.contains(pkg to (conv ?: ""))

    private fun renderList() {
        listBox.removeAllViews()
        val chosenApps = apps.count { a ->
            fullApps.contains(a.packageName) || selConvs.any { it.first == a.packageName }
        }
        summaryLine.text = getString(R.string.selected_summary, chosenApps, apps.size)
        exportBtn.isEnabled = fullApps.isNotEmpty() || selConvs.isNotEmpty()
        for (a in apps) listBox.addView(appBlock(a))
    }

    private fun appBlock(a: AppSummary): View {
        val ctx = this
        val pkg = a.packageName
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.card(ctx), 12f, ctx)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = Ui.dp(ctx, 10f)
            layoutParams = lp
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 10f), Ui.dp(ctx, 10f), Ui.dp(ctx, 12f), Ui.dp(ctx, 10f))
            val cb = CheckBox(ctx)
            cb.isChecked = fullApps.contains(pkg) || selConvs.any { it.first == pkg }
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    fullApps.add(pkg)
                } else {
                    fullApps.remove(pkg)
                }
                selConvs.removeAll { it.first == pkg }
                deferRender()
            }
            addView(cb)
            val icon = Ui.appIcon(packageManager, pkg)
            if (icon != null) {
                addView(ImageView(ctx).apply {
                    setImageDrawable(icon)
                    layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 34f), Ui.dp(ctx, 34f))
                })
            }
            addView(TextView(ctx).apply {
                text = Ui.appLabel(packageManager, pkg)
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(ctx))
                setPadding(Ui.dp(ctx, 10f), 0, Ui.dp(ctx, 6f), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                maxLines = 1
            })
            addView(TextView(ctx).apply {
                text = Ui.fmtCount(a.messageCount.toLong())
                textSize = 12f
                setTextColor(Ui.sub(ctx))
            })
            if (msgPkgs.contains(pkg)) {
                addView(TextView(ctx).apply {
                    text = if (expanded.contains(pkg)) "⌄" else "›"
                    textSize = 18f
                    setTextColor(Ui.sub(ctx))
                    setPadding(Ui.dp(ctx, 10f), 0, 0, 0)
                    isClickable = true
                    setOnClickListener {
                        if (expanded.contains(pkg)) expanded.remove(pkg) else expanded.add(pkg)
                        if (expanded.contains(pkg) && !convsCache.containsKey(pkg)) {
                            loadConvs(pkg)
                        } else {
                            deferRender()
                        }
                    }
                })
            }
        }
        block.addView(head)
        if (msgPkgs.contains(pkg) && expanded.contains(pkg)) {
            val convs = convsCache[pkg]
            if (convs == null) {
                block.addView(TextView(this).apply {
                    text = "…"
                    setTextColor(Ui.sub(ctx))
                    setPadding(Ui.dp(ctx, 52f), 0, 0, Ui.dp(ctx, 10f))
                })
            } else {
                for (conv in convs) block.addView(convRow(pkg, conv))
            }
        }
        return block
    }

    private fun convRow(pkg: String, conv: String?): View {
        val ctx = this
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 52f), Ui.dp(ctx, 7f), Ui.dp(ctx, 12f), Ui.dp(ctx, 7f))
            val cb = CheckBox(ctx)
            cb.isChecked = convSelected(pkg, conv)
            cb.setOnCheckedChangeListener { _, checked ->
                if (fullApps.contains(pkg)) {
                    // materialize: whole app -> explicit conversation set
                    fullApps.remove(pkg)
                    selConvs.removeAll { it.first == pkg }
                    val all = convsCache[pkg] ?: emptyList()
                    for (c in all) {
                        if ((c ?: "") != (conv ?: "")) selConvs.add(pkg to (c ?: ""))
                    }
                    if (checked) selConvs.add(pkg to (conv ?: ""))
                } else {
                    if (checked) selConvs.add(pkg to (conv ?: "")) else selConvs.remove(pkg to (conv ?: ""))
                }
                deferRender()
            }
            addView(cb)
            addView(TextView(ctx).apply {
                text = conv ?: Ui.appLabel(packageManager, pkg)
                textSize = 13.5f
                setTextColor(Ui.text(ctx))
                maxLines = 1
            })
        }
    }

    private fun loadConvs(pkg: String) {
        val repo = CoreRuntime.repo(this)
        io.execute {
            val convs = repo.conversationsFor(pkg)
            runOnUiThread {
                convsCache[pkg] = convs
                renderList()
            }
        }
    }

    private fun doExport() {
        val repo = CoreRuntime.repo(this)
        val full = HashSet(fullApps)
        val convs = HashSet(selConvs)
        io.execute {
            try {
                val stamp = java.text.SimpleDateFormat(
                    "yyyy-MM-dd", java.util.Locale.US
                ).format(java.util.Date())
                val file = java.io.File(cacheDir, "undelete-backup-$stamp.json")
                java.io.BufferedWriter(java.io.FileWriter(file)).use { bw ->
                    val jw = android.util.JsonWriter(bw)
                    repo.writeBackup(jw, full, convs)
                    jw.flush()
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@BackupPickerActivity, "com.dung.notifycore.fileprovider", file
                )
                runOnUiThread {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(
                        Intent.createChooser(send, getString(R.string.backup_share_title))
                    )
                }
            } catch (e: Exception) {
                // export failed silently — retrying is always safe
            }
        }
    }
}
