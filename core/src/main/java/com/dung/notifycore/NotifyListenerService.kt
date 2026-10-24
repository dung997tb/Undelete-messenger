package com.dung.notifycore

import android.app.Notification
import android.app.Person
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dung.notifycore.data.ChatMessage
import java.util.concurrent.Executors

/**
 * Captures every posted notification into the encrypted store.
 * Callbacks arrive on the main thread — all DB work is handed to a
 * single background executor, and repeat callbacks for one key are
 * treated as updates, not new records (spec amendment 2026-10-21).
 * Suite 1.0.0: chat notifications are additionally unpacked into
 * individual messages (MessagingStyle bundles / inbox lines).
 */
class NotifyListenerService : NotificationListenerService() {

    private val io = Executors.newSingleThreadExecutor()
    private val postCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val repo by lazy { CoreRuntime.repo(this) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        CoreStatus.setConnected(this, true)
        io.execute {
            repo.prune()
            repo.pruneMessages { pkg -> CoreSettings.capFor(this, pkg) }
            SmsSync.syncRecent(this@NotifyListenerService, repo)
            // Backfill whatever is still sitting in the notification shade:
            // it was posted while the listener was dead (reinstall, reboot,
            // service restart) and would otherwise never be captured.
            try {
                val actives = activeNotifications
                if (actives != null) {
                    for (sbn in actives) capture(sbn)
                }
            } catch (e: Exception) {
                // raced with a disconnect — the next connect retries
            }
            io.execute {
                repo.pruneMessages { pkg -> CoreSettings.capFor(this@NotifyListenerService, pkg) }
            }
        }
    }

    override fun onListenerDisconnected() {
        CoreStatus.setConnected(this, false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        capture(sbn)
    }

    /**
     * The single capture path — live posts and the shade backfill both
     * go through here, so treatment is identical.
     */
    private fun capture(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (CoreSettings.isBlacklisted(this, sbn.packageName)) return
        val n: Notification = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val conversation =
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: title
        val chatMsgs = extractChatMessages(n)
        val pkg = sbn.packageName
        val key = sbn.key
        val postTime = sbn.postTime
        val category = n.category
        io.execute {
            repo.upsert(key, pkg, title, text, bigText, category, postTime)
            if (chatMsgs != null) {
                // Drop system redaction placeholders — they are not content.
                val filtered = chatMsgs.filter { it.text.trim() !in SmsSync.PLACEHOLDERS }
                // Once SMS permission is granted, the SMS app's content comes
                // from the SMS store only (no double capture via notifications).
                val fromSmsStore = pkg == SmsSync.defaultSmsPackage(this@NotifyListenerService) &&
                    SmsSync.hasReadPermission(this@NotifyListenerService)
                if (!fromSmsStore) repo.storeMessages(key, pkg, conversation, filtered)
            }
            extractAvatars(n, pkg, conversation)
            CoreRuntime.dispatchPosted(pkg, title, text, postTime)
            if (postCounter.incrementAndGet() % 100 == 0) {
                repo.prune()
                repo.pruneMessages { p2 -> CoreSettings.capFor(this@NotifyListenerService, p2) }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = sbn.key
        io.execute { repo.markRemoved(key) }
    }

    /** Save sender/conversation avatars carried by this notification (1.0.2). */
    private fun extractAvatars(n: Notification, pkg: String, conversation: String?) {
        val arr = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (arr != null) {
            for (p in arr) {
                val b = p as? Bundle ?: continue
                val person = b.get("sender_person") as? Person ?: continue
                val name = person.name?.toString() ?: continue
                val bmp = Avatars.bitmapFromIcon(this, person.icon) ?: continue
                Avatars.save(this, Avatars.senderFile(this, pkg, name), bmp)
            }
        }
        if (conversation != null) {
            val bmp = Avatars.bitmapFromIcon(
                this, try { n.getLargeIcon() } catch (e: Exception) { null }
            ) ?: @Suppress("DEPRECATION") n.largeIcon
            if (bmp != null) Avatars.save(this, Avatars.convFile(this, pkg, conversation), bmp)
        }
    }

    /**
     * Pull individual messages out of a chat notification.
     * Returns null when the notification carries no message bundle
     * (plain notifications); an empty list means the bundle exists
     * but is currently empty (everything recalled/read).
     */
    private fun extractChatMessages(n: Notification): List<ChatMessage>? {
        val extras = n.extras
        val arr = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (arr != null) {
            val out = ArrayList<ChatMessage>()
            for (p in arr) {
                val b = p as? Bundle ?: continue
                val text = b.getCharSequence("text")?.toString() ?: continue
                var sender = b.getCharSequence("sender")?.toString()
                if (sender == null) {
                    val person = b.get("sender_person")
                    if (person is Person) sender = person.name?.toString()
                }
                val t = b.getLong("time", 0L)
                out.add(ChatMessage(sender, text, if (t > 0) t else System.currentTimeMillis()))
            }
            return out
        }
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (lines != null) {
            val now = System.currentTimeMillis()
            return lines.mapIndexed { i, cs -> ChatMessage(null, cs.toString(), now + i) }
        }
        return null
    }
}
