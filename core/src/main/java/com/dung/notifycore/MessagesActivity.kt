package com.dung.notifycore

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dung.notifycore.data.NotificationRow
import com.dung.notifycore.data.StoredMessage
import java.util.concurrent.Executors

/**
 * Message views:
 *  - ALL: flat log of extracted messages (fallback: notification log)
 *  - APP: conversation list -> conversation log (fallback: app notification log)
 *  - CONV: incoming-message log of one conversation. Recalled (vanished)
 *    messages render as a strong red card with a bold label — design v2.
 */
class MessagesActivity : AppCompatActivity() {

    private val io = Executors.newSingleThreadExecutor()
    private lateinit var box: LinearLayout
    private lateinit var searchInput: EditText
    private var mode: String = "all"
    private var pkg: String? = null
    private var conv: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: "all"
        pkg = intent.getStringExtra(EXTRA_PKG)
        conv = intent.getStringExtra(EXTRA_CONV)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.bg(this@MessagesActivity))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@MessagesActivity, 14f), Ui.dp(this@MessagesActivity, 14f), Ui.dp(this@MessagesActivity, 16f), Ui.dp(this@MessagesActivity, 8f))
            addView(TextView(this@MessagesActivity).apply {
                text = "‹"
                textSize = 26f
                setTextColor(Ui.ACCENT)
                setPadding(Ui.dp(this@MessagesActivity, 4f), 0, Ui.dp(this@MessagesActivity, 10f), Ui.dp(this@MessagesActivity, 2f))
                setOnClickListener { finish() }
            })
            addView(TextView(this@MessagesActivity).apply {
                text = when (mode) {
                    "conv" -> conv ?: Ui.appLabel(packageManager, pkg ?: "")
                    "app" -> Ui.appLabel(packageManager, pkg ?: "")
                    "search" -> getString(R.string.search_title)
                    else -> getString(R.string.all_messages)
                }
                textSize = 19f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.text(this@MessagesActivity))
                maxLines = 1
            })
        }
        root.addView(header)
        if (mode == "conv" && pkg != null) {
            val who = conv ?: ""
            val hBmp = Avatars.loadCached(Avatars.convFile(this, pkg!!, who))
                ?: Avatars.loadCached(Avatars.senderFile(this, pkg!!, who))
            val hAv = Ui.avatarOrLetter(this, hBmp, who, 34f, pkg!! + who)
            (hAv.layoutParams as LinearLayout.LayoutParams).rightMargin = Ui.dp(this, 10f)
            header.addView(hAv, 1)
        }

        val scroll = ScrollView(this)
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MessagesActivity, 16f), Ui.dp(this@MessagesActivity, 4f), Ui.dp(this@MessagesActivity, 16f), Ui.dp(this@MessagesActivity, 24f))
        }
        scroll.addView(box)
        if (mode == "search") {
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(this@MessagesActivity, 16f), Ui.dp(this@MessagesActivity, 2f), Ui.dp(this@MessagesActivity, 16f), Ui.dp(this@MessagesActivity, 8f))
                searchInput = EditText(this@MessagesActivity).apply {
                    hint = getString(R.string.search_box_hint)
                    textSize = 14f
                    isSingleLine = true
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(searchInput)
                addView(Button(this@MessagesActivity).apply {
                    text = getString(R.string.search_action)
                    setOnClickListener { load() }
                    val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp2.leftMargin = Ui.dp(this@MessagesActivity, 8f)
                    layoutParams = lp2
                })
            }
            root.addView(bar)
        }
        root.addView(scroll)
        setContentView(root)
        root.setPadding(0, Ui.statusBarHeight(this), 0, 0)

        load()
    }

    private fun load() {
        val repo = CoreRuntime.repo(this)
        val q = if (mode == "search" && ::searchInput.isInitialized) searchInput.text.toString().trim() else ""
        io.execute {
            when (mode) {
                "search" -> {
                    if (q.isBlank()) {
                        runOnUiThread { renderCenterText(getString(R.string.search_prompt)) }
                    } else {
                        val msgs = repo.searchMessages(q, 300)
                        runOnUiThread {
                            if (msgs.isEmpty()) renderCenterText(getString(R.string.search_empty))
                            else renderMessageLog(msgs, showNote = false, showApp = true)
                        }
                    }
                }
                "conv" -> {
                    val msgs = repo.messagesFor(pkg!!, conv, 500)
                    runOnUiThread { renderMessageLog(msgs, showNote = true, showApp = false) }
                }
                "app" -> {
                    val convs = repo.conversationsFor(pkg!!)
                    if (convs.isNotEmpty()) {
                        val previews = convs.map { c -> c to repo.messagesFor(pkg!!, c, 500).firstOrNull() }
                        runOnUiThread { renderConversations(previews) }
                    } else {
                        val notifs = repo.notificationsFor(pkg!!, 200)
                        runOnUiThread { renderNotificationLog(notifs) }
                    }
                }
                else -> {
                    val msgs = repo.latestMessages(300)
                    if (msgs.isNotEmpty()) {
                        runOnUiThread { renderMessageLog(msgs, showNote = false, showApp = true) }
                    } else {
                        val notifs = repo.latest(300)
                        runOnUiThread { renderNotificationLog(notifs) }
                    }
                }
            }
        }
    }

    private fun renderCenterText(text: String) {
        box.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.card(this@MessagesActivity), 14f, this@MessagesActivity)
            setPadding(Ui.dp(this@MessagesActivity, 16f), Ui.dp(this@MessagesActivity, 16f), Ui.dp(this@MessagesActivity, 16f), Ui.dp(this@MessagesActivity, 16f))
            addView(TextView(this@MessagesActivity).apply {
                this.text = text
                textSize = 13f
                setTextColor(Ui.sub(this@MessagesActivity))
            })
        }
        box.addView(card)
    }

    private fun renderConversations(items: List<Pair<String?, StoredMessage?>>) {
        box.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(Ui.card(this@MessagesActivity), 14f, this@MessagesActivity)
        }
        if (items.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = getString(R.string.hint_pin_conv)
                textSize = 11.5f
                setTextColor(Ui.sub(this@MessagesActivity))
                setPadding(Ui.dp(this@MessagesActivity, 14f), Ui.dp(this@MessagesActivity, 9f), Ui.dp(this@MessagesActivity, 14f), Ui.dp(this@MessagesActivity, 3f))
            })
        }
        val sortedItems = items.sortedByDescending { CoreSettings.isConvPinned(this, pkg ?: "", it.first) }
        for ((c, latest) in sortedItems) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(this@MessagesActivity, 13f), Ui.dp(this@MessagesActivity, 10f), Ui.dp(this@MessagesActivity, 13f), Ui.dp(this@MessagesActivity, 10f))
                isClickable = true
                setOnClickListener { startConv(this@MessagesActivity, pkg!!, c) }
                setOnLongClickListener {
                    CoreSettings.setConvPinned(
                        this@MessagesActivity, pkg!!, c,
                        !CoreSettings.isConvPinned(this@MessagesActivity, pkg!!, c)
                    )
                    load()
                    true
                }
                val convName = c ?: Ui.appLabel(packageManager, pkg ?: "")
                val avBmp = Avatars.loadCached(Avatars.convFile(this@MessagesActivity, pkg!!, convName))
                    ?: Avatars.loadCached(Avatars.senderFile(this@MessagesActivity, pkg!!, convName))
                addView(Ui.avatarOrLetter(this@MessagesActivity, avBmp, convName, 40f, pkg!! + convName))
                val texts = LinearLayout(this@MessagesActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(Ui.dp(this@MessagesActivity, 11f), 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MessagesActivity).apply {
                        text = if (CoreSettings.isConvPinned(this@MessagesActivity, pkg!!, c)) "\uD83D\uDCCC " + convName else convName
                        textSize = 14.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Ui.text(this@MessagesActivity))
                        maxLines = 1
                    })
                    addView(TextView(this@MessagesActivity).apply {
                        text = latest?.text ?: ""
                        textSize = 12.5f
                        maxLines = 1
                        setTextColor(Ui.sub(this@MessagesActivity))
                    })
                }
                addView(texts)
            }
            card.addView(row)
        }
        box.addView(card)
    }

    private fun renderMessageLog(msgs: List<StoredMessage>, showNote: Boolean, showApp: Boolean) {
        box.removeAllViews()
        if (showNote) {
            box.addView(TextView(this).apply {
                text = getString(R.string.incoming_only_note)
                textSize = 11.5f
                setTextColor(Ui.sub(this@MessagesActivity))
                background = Ui.rounded(Ui.warnBg(this@MessagesActivity), 10f, this@MessagesActivity)
                setPadding(Ui.dp(this@MessagesActivity, 11f), Ui.dp(this@MessagesActivity, 8f), Ui.dp(this@MessagesActivity, 11f), Ui.dp(this@MessagesActivity, 8f))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = Ui.dp(this@MessagesActivity, 10f)
                layoutParams = lp
            })
        }
        if (msgs.isEmpty()) {
            box.addView(TextView(this).apply {
                text = getString(R.string.no_messages_yet)
                textSize = 13.5f
                setTextColor(Ui.sub(this@MessagesActivity))
            })
            return
        }
        for (m in msgs) box.addView(messageCard(m, showApp))
        box.addView(TextView(this).apply {
            text = getString(R.string.hint_open_app)
            textSize = 11.5f
            setTextColor(Ui.sub(this@MessagesActivity))
            setPadding(Ui.dp(this@MessagesActivity, 4f), Ui.dp(this@MessagesActivity, 2f), Ui.dp(this@MessagesActivity, 4f), 0)
        })
    }

    /** Tap a message: jump to the app it came from (its main screen). */
    private fun openSourceApp(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            // no launcher entry (system component) — nothing to open
        }
    }

    private fun messageCard(m: StoredMessage, showApp: Boolean): View {
        val ctx = this
        val vanished = false // 1.3.2: recall label removed by request; vanished_at still recorded
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = Ui.dp(ctx, 8f)
            layoutParams = lp
        }
        if (vanished) {
            outer.background = Ui.rounded(Ui.redBg(ctx), 12f, ctx)
            outer.addView(View(this).apply {
                setBackgroundColor(Ui.RED)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 6f), ViewGroup.LayoutParams.MATCH_PARENT)
            })
        } else {
            outer.background = Ui.rounded(Ui.card(ctx), 12f, ctx)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 12f), Ui.dp(ctx, 9f), Ui.dp(ctx, 12f), Ui.dp(ctx, 9f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(ctx).apply {
                    text = listOfNotNull(
                        if (showApp) Ui.appLabel(packageManager, m.packageName) else null,
                        m.sender ?: m.conversation
                    ).joinToString(" · ").ifEmpty { Ui.appLabel(packageManager, m.packageName) }
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (vanished) Ui.RED_DARK else Ui.text(ctx))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    maxLines = 1
                })
                addView(TextView(ctx).apply {
                    text = Ui.fmtWhen(m.msgTime)
                    textSize = 10.5f
                    setTextColor(Ui.sub(ctx))
                })
            }
            addView(head)
            addView(TextView(ctx).apply {
                text = m.text ?: ""
                textSize = 14f
                setTextColor(Ui.text(ctx))
                val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp2.topMargin = Ui.dp(ctx, 3f)
                layoutParams = lp2
            })
            if (vanished) {
                val tagRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val lp3 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp3.topMargin = Ui.dp(ctx, 7f)
                    layoutParams = lp3
                    addView(TextView(ctx).apply {
                        text = getString(R.string.chip_recalled)
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        background = Ui.rounded(Ui.RED, 6f, ctx)
                        setPadding(Ui.dp(ctx, 7f), Ui.dp(ctx, 2.5f), Ui.dp(ctx, 7f), Ui.dp(ctx, 2.5f))
                    })
                    addView(TextView(ctx).apply {
                        text = getString(R.string.recalled_saved_note)
                        textSize = 10.5f
                        setTextColor(Ui.RED_DARK)
                        setPadding(Ui.dp(ctx, 7f), 0, 0, 0)
                    })
                }
                addView(tagRow)
            }
        }
        val who = m.sender ?: m.conversation ?: "?"
        val avBmp2 = Avatars.loadCached(Avatars.senderFile(ctx, m.packageName, who))
        val avView = Ui.avatarOrLetter(ctx, avBmp2, who, 30f, m.packageName + who)
        (avView.layoutParams as LinearLayout.LayoutParams).apply {
            topMargin = Ui.dp(ctx, 9f)
            leftMargin = if (vanished) Ui.dp(ctx, 8f) else Ui.dp(ctx, 10f)
        }
        outer.addView(avView)
        outer.addView(inner)
        outer.isClickable = true
        outer.setOnClickListener { openSourceApp(m.packageName) }
        return outer
    }

    private fun renderNotificationLog(rows: List<NotificationRow>) {
        box.removeAllViews()
        if (rows.isEmpty()) {
            box.addView(TextView(this).apply {
                text = getString(R.string.no_messages_yet)
                textSize = 13.5f
                setTextColor(Ui.sub(this@MessagesActivity))
            })
            return
        }
        for (r in rows) {
            val ctx = this
            val removed = r.removedAt != null
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.rounded(Ui.card(ctx), 12f, ctx)
                setPadding(Ui.dp(ctx, 12f), Ui.dp(ctx, 9f), Ui.dp(ctx, 12f), Ui.dp(ctx, 9f))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = Ui.dp(ctx, 8f)
                layoutParams = lp
                addView(TextView(ctx).apply {
                    text = listOfNotNull(r.title, Ui.fmtWhen(r.receivedAt)).joinToString(" · ")
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Ui.text(ctx))
                    maxLines = 1
                })
                addView(TextView(ctx).apply {
                    text = r.bigText ?: r.text ?: ""
                    textSize = 13.5f
                    setTextColor(Ui.text(ctx))
                })
                if (removed) {
                    addView(TextView(ctx).apply {
                        text = getString(R.string.notif_removed)
                        textSize = 10.5f
                        setTextColor(Ui.RED_DARK)
                        val lp2 = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        lp2.topMargin = Ui.dp(ctx, 5f)
                        layoutParams = lp2
                    })
                }
            }
            card.isClickable = true
            card.setOnClickListener { openSourceApp(r.packageName) }
            box.addView(card)
        }
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_CONV = "conv"

        fun startSearch(ctx: Context) {
            ctx.startActivity(Intent(ctx, MessagesActivity::class.java).putExtra("mode", "search"))
        }

        fun startAll(ctx: Context) {
            ctx.startActivity(Intent(ctx, MessagesActivity::class.java).putExtra(EXTRA_MODE, "all"))
        }

        fun startApp(ctx: Context, pkg: String) {
            ctx.startActivity(
                Intent(ctx, MessagesActivity::class.java)
                    .putExtra(EXTRA_MODE, "app").putExtra(EXTRA_PKG, pkg)
            )
        }

        fun startConv(ctx: Context, pkg: String, conv: String?) {
            ctx.startActivity(
                Intent(ctx, MessagesActivity::class.java)
                    .putExtra(EXTRA_MODE, "conv").putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_CONV, conv)
            )
        }
    }
}
