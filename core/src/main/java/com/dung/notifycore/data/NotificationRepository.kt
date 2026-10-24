package com.dung.notifycore.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

data class NotificationRow(
    val key: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val category: String?,
    val postTime: Long,
    val receivedAt: Long,
    val updatedAt: Long,
    val updateCount: Int,
    val removedAt: Long?
)

/** One message extracted from a chat notification bundle (MessagingStyle). */
data class ChatMessage(
    val sender: String?,
    val text: String,
    val msgTime: Long
)

data class StoredMessage(
    val id: Long,
    val sbnKey: String,
    val packageName: String,
    val conversation: String?,
    val sender: String?,
    val text: String?,
    val msgTime: Long,
    val receivedAt: Long,
    val vanishedAt: Long?
)

data class AppSummary(
    val packageName: String,
    val messageCount: Int,
    val latestText: String?,
    val latestTime: Long,
    val latestVanished: Boolean = false
)

/**
 * Captured-notification store (SQLCipher).
 * v1: one row per StatusBarNotification.key; updates merge, removals stamp.
 * v2 (suite 1.0.0): adds per-message extraction for chat notifications —
 * every message inside a MessagingStyle bundle is stored individually,
 * and a message that later disappears from the bundle (recall/edit-away)
 * is stamped vanished_at instead of being lost.
 */
class NotificationRepository(context: Context) {

    companion object {
        const val RETENTION_DAYS = 30
        const val MAX_ROWS = 10_000
        private const val TABLE = "notifications"
        private const val TABLE_MSG = "messages"
    }

    private val helper: SQLiteOpenHelper
    private val pass: String = DbKeyManager.getPassphrase(context.applicationContext)

    init {
        System.loadLibrary("sqlcipher")
        helper = object : SQLiteOpenHelper(
            context.applicationContext, "notifycore.db", pass, null, 2, 0, null, null, true
        ) {
            override fun onCreate(db: SQLiteDatabase) {
                createNotifications(db)
                createMessages(db)
            }

            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
                if (old < 2) createMessages(db)
            }

            private fun createNotifications(db: SQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS $TABLE (
                        sbn_key TEXT PRIMARY KEY,
                        package_name TEXT NOT NULL,
                        title TEXT, text TEXT, big_text TEXT, category TEXT,
                        post_time INTEGER NOT NULL,
                        received_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        update_count INTEGER NOT NULL DEFAULT 1,
                        removed_at INTEGER
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_received ON $TABLE(received_at DESC)")
            }

            private fun createMessages(db: SQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS $TABLE_MSG (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sbn_key TEXT NOT NULL,
                        package_name TEXT NOT NULL,
                        conversation TEXT,
                        sender TEXT,
                        text TEXT,
                        msg_time INTEGER NOT NULL,
                        received_at INTEGER NOT NULL,
                        vanished_at INTEGER,
                        UNIQUE(sbn_key, msg_time, sender, text)
                    )"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_msg_conv ON $TABLE_MSG(package_name, conversation, msg_time)"
                )
            }
        }
    }

    private val db: SQLiteDatabase
        get() = helper.writableDatabase

    // ---------------- notifications (v1 behaviour, unchanged) ----------------

    @Synchronized
    fun upsert(
        key: String, pkg: String, title: String?, text: String?, bigText: String?,
        category: String?, postTime: Long
    ) {
        val now = System.currentTimeMillis()
        val d = db
        val existing = d.query(
            TABLE, arrayOf("update_count"), "sbn_key = ?", arrayOf(key), null, null, null
        ).use { if (it.moveToFirst()) it.getInt(0) else null }
        val values = ContentValues().apply {
            put("package_name", pkg)
            put("title", title)
            put("text", text)
            put("big_text", bigText)
            put("category", category)
            put("post_time", postTime)
            put("updated_at", now)
        }
        if (existing == null) {
            values.put("sbn_key", key)
            values.put("received_at", now)
            values.put("update_count", 1)
            d.insert(TABLE, null, values)
        } else {
            values.put("update_count", existing + 1)
            d.update(TABLE, values, "sbn_key = ?", arrayOf(key))
        }
    }

    @Synchronized
    fun markRemoved(key: String) {
        // 1.0.2: only the notification row is stamped. Its messages keep
        // their state — a dismissed notification is not a recalled chat.
        val now = System.currentTimeMillis()
        val nv = ContentValues().apply { put("removed_at", now) }
        db.update(TABLE, nv, "sbn_key = ? AND removed_at IS NULL", arrayOf(key))
    }

    @Synchronized
    fun count(): Long = db.query(TABLE, arrayOf("COUNT(*)"), null, null, null, null, null)
        .use { if (it.moveToFirst()) it.getLong(0) else 0L }

    @Synchronized
    fun latest(limit: Int): List<NotificationRow> {
        val out = ArrayList<NotificationRow>()
        db.query(
            TABLE,
            arrayOf(
                "sbn_key", "package_name", "title", "text", "big_text", "category",
                "post_time", "received_at", "updated_at", "update_count", "removed_at"
            ),
            null, null, null, null, "received_at DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) out.add(rowFrom(c))
        }
        return out
    }

    fun latestCursor(limit: Int): Cursor = db.query(
        TABLE,
        arrayOf(
            "sbn_key", "package_name", "title", "text", "big_text", "category",
            "post_time", "received_at", "updated_at", "update_count", "removed_at"
        ),
        null, null, null, null, "received_at DESC", limit.toString()
    )

    // ---------------- messages (v2) ----------------

    /**
     * Store the messages of one chat-notification snapshot.
     * Messages are deduplicated by (sbn_key, msg_time, sender, text);
     * previously stored messages of this notification that are absent from
     * the new snapshot are stamped vanished (recall signal).
     */
    /**
     * Store the messages of one chat-notification snapshot (1.0.2 rules).
     * Snapshots are compared per (sender, msg_time) group:
     *  - same text on both sides -> unchanged
     *  - text changed inside a group -> EDIT: the stored text is updated
     *    in place, never flagged (channels edit posts constantly)
     *  - a stored message missing from the snapshot is flagged vanished
     *    ONLY when it disappears from inside the snapshot's time window
     *    (its msg_time is >= the snapshot's oldest message). A message
     *    older than the whole snapshot most likely just fell out of the
     *    bundle window the chat app displays — not a recall.
     */
    @Synchronized
    fun storeMessages(sbnKey: String, pkg: String, conversation: String?, msgs: List<ChatMessage>) {
        val d = db
        val now = System.currentTimeMillis()
        if (msgs.isEmpty()) return
        val bundleMin = msgs.minOf { it.msgTime }

        data class Row(val id: Long, val sender: String?, val text: String?, val msgTime: Long)

        val existing = ArrayList<Row>()
        d.query(
            TABLE_MSG, arrayOf("id", "sender", "text", "msg_time"),
            "sbn_key = ? AND vanished_at IS NULL", arrayOf(sbnKey), null, null, "msg_time ASC"
        ).use { c ->
            while (c.moveToNext()) {
                existing.add(
                    Row(
                        c.getLong(0),
                        if (c.isNull(1)) null else c.getString(1),
                        if (c.isNull(2)) null else c.getString(2),
                        c.getLong(3)
                    )
                )
            }
        }

        fun gkey(sender: String?, t: Long): String = (sender ?: "") + "\u0001" + t
        val inByGroup = msgs.groupBy { gkey(it.sender, it.msgTime) }
        val exByGroup = existing.groupBy { gkey(it.sender, it.msgTime) }

        d.beginTransaction()
        try {
            fun insert(m: ChatMessage) {
                val v = ContentValues().apply {
                    put("sbn_key", sbnKey)
                    put("package_name", pkg)
                    put("conversation", conversation)
                    put("sender", m.sender)
                    put("text", m.text)
                    put("msg_time", m.msgTime)
                    put("received_at", now)
                }
                d.insertWithOnConflict(TABLE_MSG, null, v, SQLiteDatabase.CONFLICT_IGNORE)
            }

            fun vanish(id: Long) {
                val v = ContentValues().apply { put("vanished_at", now) }
                d.update(TABLE_MSG, v, "id = ?", arrayOf(id.toString()))
            }

            fun judge(row: Row) {
                if (row.msgTime >= bundleMin) vanish(row.id)
            }

            for ((gk, inGroup) in inByGroup) {
                val exGroup = exByGroup[gk].orEmpty()
                val pool = exGroup.toMutableList()
                val unmatchedNew = ArrayList<ChatMessage>()
                for (m in inGroup) {
                    val idx = pool.indexOfFirst { it.text == m.text }
                    if (idx >= 0) pool.removeAt(idx) else unmatchedNew.add(m)
                }
                val matchedTexts = exGroup.filter { it !in pool }.map { it.text }.toHashSet()
                val pairs = minOf(pool.size, unmatchedNew.size)
                for (i in 0 until pairs) {
                    if (unmatchedNew[i].text in matchedTexts) {
                        // another stored row already carries the new text;
                        // updating would violate the unique key — judge instead
                        judge(pool[i])
                    } else {
                        val v = ContentValues().apply { put("text", unmatchedNew[i].text) }
                        d.update(TABLE_MSG, v, "id = ?", arrayOf(pool[i].id.toString()))
                    }
                }
                for (i in pairs until unmatchedNew.size) insert(unmatchedNew[i])
                for (i in pairs until pool.size) judge(pool[i])
            }
            for ((gk, exGroup) in exByGroup) {
                if (gk in inByGroup) continue
                for (row in exGroup) judge(row)
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    @Synchronized
    fun messageCount(): Long =
        db.query(TABLE_MSG, arrayOf("COUNT(*)"), null, null, null, null, null)
            .use { if (it.moveToFirst()) it.getLong(0) else 0L }

    fun messagesCursor(limit: Int): Cursor = db.query(
        TABLE_MSG,
        arrayOf(
            "sbn_key", "package_name", "conversation", "sender", "text",
            "msg_time", "received_at", "vanished_at"
        ),
        null, null, null, null, "msg_time DESC", limit.toString()
    )

    @Synchronized
    fun latestMessages(limit: Int): List<StoredMessage> {
        val out = ArrayList<StoredMessage>()
        db.query(
            TABLE_MSG,
            arrayOf(
                "id", "sbn_key", "package_name", "conversation", "sender", "text",
                "msg_time", "received_at", "vanished_at"
            ),
            null, null, null, null, "msg_time DESC", limit.toString()
        ).use { c -> while (c.moveToNext()) out.add(msgFrom(c)) }
        return out
    }

    /** Per-app rollup for the home screen: message count + latest message. */
    @Synchronized
    fun appSummaries(): List<AppSummary> {
        val out = ArrayList<AppSummary>()
        db.rawQuery(
            "SELECT package_name, COUNT(*), " +
                "(SELECT text FROM $TABLE_MSG m2 WHERE m2.package_name = m.package_name " +
                " ORDER BY msg_time DESC LIMIT 1), MAX(msg_time), " +
                "(SELECT vanished_at FROM $TABLE_MSG m3 WHERE m3.package_name = m.package_name " +
                " ORDER BY msg_time DESC LIMIT 1) " +
                "FROM $TABLE_MSG m GROUP BY package_name ORDER BY MAX(msg_time) DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    AppSummary(
                        packageName = c.getString(0),
                        messageCount = c.getInt(1),
                        latestText = if (c.isNull(2)) null else c.getString(2),
                        latestTime = c.getLong(3),
                        latestVanished = !c.isNull(4)
                    )
                )
            }
        }
        return out
    }

    @Synchronized
    fun messagesFor(pkg: String, conversation: String?, limit: Int): List<StoredMessage> {
        val out = ArrayList<StoredMessage>()
        val sel: String
        val args: Array<String>
        if (conversation == null) {
            sel = "package_name = ? AND conversation IS NULL"
            args = arrayOf(pkg)
        } else {
            sel = "package_name = ? AND conversation = ?"
            args = arrayOf(pkg, conversation)
        }
        db.query(
            TABLE_MSG,
            arrayOf(
                "id", "sbn_key", "package_name", "conversation", "sender", "text",
                "msg_time", "received_at", "vanished_at"
            ),
            sel, args, null, null, "msg_time ASC", limit.toString()
        ).use { c -> while (c.moveToNext()) out.add(msgFrom(c)) }
        return out
    }

    /** Distinct conversations of one app, most recent first. */
    @Synchronized
    fun conversationsFor(pkg: String): List<String?> {
        val out = ArrayList<String?>()
        db.rawQuery(
            "SELECT conversation, MAX(msg_time) FROM $TABLE_MSG WHERE package_name = ? " +
                "GROUP BY conversation ORDER BY MAX(msg_time) DESC",
            arrayOf(pkg)
        ).use { c ->
            while (c.moveToNext()) out.add(if (c.isNull(0)) null else c.getString(0))
        }
        return out
    }

    /** Packages that have produced captures (for pickers/blacklist). */
    @Synchronized
    fun knownPackages(): List<String> {
        val out = ArrayList<String>()
        db.rawQuery(
            "SELECT package_name FROM $TABLE UNION SELECT package_name FROM $TABLE_MSG " +
                "ORDER BY package_name", null
        ).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    /** Retention: trim each app's stored messages to its cap (<0 = keep all). */
    @Synchronized
    fun pruneMessages(capFor: (String) -> Int) {
        val pkgs = ArrayList<String>()
        db.rawQuery("SELECT DISTINCT package_name FROM $TABLE_MSG", null)
            .use { c -> while (c.moveToNext()) pkgs.add(c.getString(0)) }
        for (pkg in pkgs) {
            val cap = capFor(pkg)
            if (cap < 0) continue
            db.execSQL(
                "DELETE FROM $TABLE_MSG WHERE package_name = ? AND id NOT IN " +
                    "(SELECT id FROM $TABLE_MSG WHERE package_name = ? " +
                    "ORDER BY msg_time DESC, id DESC LIMIT ?)",
                arrayOf(pkg, pkg, cap)
            )
        }
    }

    /** Latest notification per app (home-screen fallback for apps with no extracted messages yet). */
    @Synchronized
    fun latestNotificationsByApp(): List<AppSummary> {
        val out = ArrayList<AppSummary>()
        db.rawQuery(
            "SELECT package_name, COUNT(*), " +
                "(SELECT COALESCE(big_text, text, title) FROM $TABLE n2 " +
                " WHERE n2.package_name = n.package_name ORDER BY received_at DESC LIMIT 1), " +
                "MAX(received_at) FROM $TABLE n GROUP BY package_name ORDER BY MAX(received_at) DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    AppSummary(
                        packageName = c.getString(0),
                        messageCount = c.getInt(1),
                        latestText = if (c.isNull(2)) null else c.getString(2),
                        latestTime = c.getLong(3),
                        latestVanished = false
                    )
                )
            }
        }
        return out
    }

    @Synchronized
    fun notificationsFor(pkg: String, limit: Int): List<NotificationRow> {
        val out = ArrayList<NotificationRow>()
        db.query(
            TABLE,
            arrayOf(
                "sbn_key", "package_name", "title", "text", "big_text", "category",
                "post_time", "received_at", "updated_at", "update_count", "removed_at"
            ),
            "package_name = ?", arrayOf(pkg), null, null, "received_at DESC", limit.toString()
        ).use { c -> while (c.moveToNext()) out.add(rowFrom(c)) }
        return out
    }

    @Synchronized
    fun deleteAll() {
        db.delete(TABLE, null, null)
        db.delete(TABLE_MSG, null, null)
    }

    @Synchronized
    fun prune() {
        val d = db
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 86_400_000L
        d.delete(TABLE, "received_at < ?", arrayOf(cutoff.toString()))
        d.execSQL(
            "DELETE FROM $TABLE WHERE sbn_key IN (SELECT sbn_key FROM $TABLE " +
                "ORDER BY received_at DESC LIMIT -1 OFFSET $MAX_ROWS)"
        )
    }

    private fun naturalKey(sender: String?, text: String?, msgTime: Long): String =
        "$msgTime|${sender ?: ""}|${text ?: ""}"

    private fun rowFrom(c: Cursor): NotificationRow {
        fun str(i: Int): String? = if (c.isNull(i)) null else c.getString(i)
        return NotificationRow(
            key = c.getString(0), packageName = c.getString(1),
            title = str(2), text = str(3), bigText = str(4), category = str(5),
            postTime = c.getLong(6), receivedAt = c.getLong(7), updatedAt = c.getLong(8),
            updateCount = c.getInt(9),
            removedAt = if (c.isNull(10)) null else c.getLong(10)
        )
    }

    private fun msgFrom(c: Cursor): StoredMessage {
        fun str(i: Int): String? = if (c.isNull(i)) null else c.getString(i)
        return StoredMessage(
            id = c.getLong(0), sbnKey = c.getString(1), packageName = c.getString(2),
            conversation = str(3), sender = str(4), text = str(5),
            msgTime = c.getLong(6), receivedAt = c.getLong(7),
            vanishedAt = if (c.isNull(8)) null else c.getLong(8)
        )
    }

    /** Search saved messages by text, sender or conversation (1.1.0). */
    @Synchronized
    fun searchMessages(query: String, limit: Int): List<StoredMessage> {
        val like = "%" + query.replace("%", " ").replace("_", " ") + "%"
        val out = ArrayList<StoredMessage>()
        db.query(
            TABLE_MSG,
            arrayOf(
                "id", "sbn_key", "package_name", "conversation", "sender", "text",
                "msg_time", "received_at", "vanished_at"
            ),
            "text LIKE ? OR sender LIKE ? OR conversation LIKE ?",
            arrayOf(like, like, like), null, null, "msg_time DESC", limit.toString()
        ).use { c -> while (c.moveToNext()) out.add(msgFrom(c)) }
        return out
    }

    /** Remove every recalled flag (messages themselves are kept). */
    @Synchronized
    fun clearVanishedFlags(): Int {
        val v = ContentValues()
        v.putNull("vanished_at")
        return db.update(TABLE_MSG, v, "vanished_at IS NOT NULL", null)
    }

    /** When the listener last processed anything (null = never). */
    @Synchronized
    fun lastCaptureAt(): Long? {
        var best: Long? = null
        db.query(TABLE, arrayOf("MAX(updated_at)"), null, null, null, null, null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) best = c.getLong(0)
        }
        db.query(TABLE_MSG, arrayOf("MAX(received_at)"), null, null, null, null, null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                val t = c.getLong(0)
                if (best == null || t > best!!) best = t
            }
        }
        return best
    }

    /**
     * Store real SMS read from the system SMS provider (1.2.0).
     * Items are (address, body, dateMillis). The synthetic sbn_key keeps
     * re-syncs idempotent through the unique key; SMS rows never vanish.
     */
    @Synchronized
    fun storeSmsMessages(pkg: String, items: List<Triple<String?, String, Long>>) {
        val d = db
        val now = System.currentTimeMillis()
        d.beginTransaction()
        try {
            for ((address, body, date) in items) {
                val v = ContentValues().apply {
                    put("sbn_key", "sms:" + (address ?: "") + ":" + date)
                    put("package_name", pkg)
                    put("conversation", address)
                    put("sender", address)
                    put("text", body)
                    put("msg_time", date)
                    put("received_at", now)
                }
                d.insertWithOnConflict(TABLE_MSG, null, v, SQLiteDatabase.CONFLICT_IGNORE)
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    /** Delete fake messages whose text is a system redaction placeholder. */
    @Synchronized
    fun deletePlaceholderMessages(pkg: String, placeholders: Collection<String>) {
        for (ph in placeholders) {
            db.delete(TABLE_MSG, "package_name = ? AND text = ?", arrayOf(pkg, ph))
        }
    }

    // ---------------- backup / restore (1.3.0) ----------------

    private fun jwStr(w: android.util.JsonWriter, name: String, v: String?) {
        if (v == null) w.name(name).nullValue() else w.name(name).value(v)
    }

    private fun jwLongOrNull(w: android.util.JsonWriter, name: String, v: Long?) {
        if (v == null) w.name(name).nullValue() else w.name(name).value(v)
    }

    /** Stream the whole store as one JSON document (backup file). */
    @Synchronized
    fun writeBackup(
        w: android.util.JsonWriter,
        fullApps: Set<String>? = null,
        convs: Set<Pair<String, String>>? = null
    ) {
        val d = db
        val convPkgs = convs?.map { it.first }?.toSet() ?: emptySet()
        w.beginObject()
        w.name("app").value("Undelete Messages")
        w.name("backup_version").value(1)
        w.name("messages")
        w.beginArray()
        d.rawQuery(
            "SELECT sbn_key, package_name, conversation, sender, text, msg_time, received_at, vanished_at " +
                "FROM $TABLE_MSG ORDER BY id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val pkg = c.getString(1)
                val conv = if (c.isNull(2)) null else c.getString(2)
                if (fullApps != null && pkg !in fullApps &&
                    !(convs != null && (pkg to (conv ?: "")) in convs)
                ) continue
                w.beginObject()
                w.name("sbn_key").value(c.getString(0))
                w.name("package_name").value(pkg)
                jwStr(w, "conversation", conv)
                jwStr(w, "sender", if (c.isNull(3)) null else c.getString(3))
                jwStr(w, "text", if (c.isNull(4)) null else c.getString(4))
                w.name("msg_time").value(c.getLong(5))
                w.name("received_at").value(c.getLong(6))
                jwLongOrNull(w, "vanished_at", if (c.isNull(7)) null else c.getLong(7))
                w.endObject()
            }
        }
        w.endArray()
        w.name("notifications")
        w.beginArray()
        d.rawQuery(
            "SELECT sbn_key, package_name, title, text, big_text, category, post_time, received_at, updated_at, update_count, removed_at " +
                "FROM $TABLE ORDER BY received_at",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val pkg = c.getString(1)
                if (fullApps != null && pkg !in fullApps && pkg !in convPkgs) continue
                w.beginObject()
                w.name("sbn_key").value(c.getString(0))
                w.name("package_name").value(pkg)
                jwStr(w, "title", if (c.isNull(2)) null else c.getString(2))
                jwStr(w, "text", if (c.isNull(3)) null else c.getString(3))
                jwStr(w, "big_text", if (c.isNull(4)) null else c.getString(4))
                jwStr(w, "category", if (c.isNull(5)) null else c.getString(5))
                w.name("post_time").value(c.getLong(6))
                w.name("received_at").value(c.getLong(7))
                w.name("updated_at").value(c.getLong(8))
                w.name("update_count").value(c.getInt(9))
                jwLongOrNull(w, "removed_at", if (c.isNull(10)) null else c.getLong(10))
                w.endObject()
            }
        }
        w.endArray()
        w.endObject()
    }

    /**
     * Merge a backup back in. Duplicates are ignored through the tables'
     * unique keys, so restoring over existing data is safe.
     * Returns (messages, notifications) actually inserted.
     */
    @Synchronized
    fun readBackup(r: android.util.JsonReader): Pair<Int, Int> {
        val d = db
        var mIns = 0
        var nIns = 0
        d.beginTransaction()
        try {
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "messages" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            val v = ContentValues()
                            r.beginObject()
                            while (r.hasNext()) {
                                val field = r.nextName()
                                if (r.peek() == android.util.JsonToken.NULL) {
                                    r.nextNull()
                                    continue
                                }
                                when (field) {
                                    "sbn_key" -> v.put("sbn_key", r.nextString())
                                    "package_name" -> v.put("package_name", r.nextString())
                                    "conversation" -> v.put("conversation", r.nextString())
                                    "sender" -> v.put("sender", r.nextString())
                                    "text" -> v.put("text", r.nextString())
                                    "msg_time" -> v.put("msg_time", r.nextLong())
                                    "received_at" -> v.put("received_at", r.nextLong())
                                    "vanished_at" -> v.put("vanished_at", r.nextLong())
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            if (d.insertWithOnConflict(TABLE_MSG, null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L) mIns++
                        }
                        r.endArray()
                    }
                    "notifications" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            val v = ContentValues()
                            r.beginObject()
                            while (r.hasNext()) {
                                val field = r.nextName()
                                if (r.peek() == android.util.JsonToken.NULL) {
                                    r.nextNull()
                                    continue
                                }
                                when (field) {
                                    "sbn_key" -> v.put("sbn_key", r.nextString())
                                    "package_name" -> v.put("package_name", r.nextString())
                                    "title" -> v.put("title", r.nextString())
                                    "text" -> v.put("text", r.nextString())
                                    "big_text" -> v.put("big_text", r.nextString())
                                    "category" -> v.put("category", r.nextString())
                                    "post_time" -> v.put("post_time", r.nextLong())
                                    "received_at" -> v.put("received_at", r.nextLong())
                                    "updated_at" -> v.put("updated_at", r.nextLong())
                                    "update_count" -> v.put("update_count", r.nextLong())
                                    "removed_at" -> v.put("removed_at", r.nextLong())
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            if (d.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L) nIns++
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
        return Pair(mIns, nIns)
    }
}
