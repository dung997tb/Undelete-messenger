package com.dung.notifycore

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.dung.notifycore.data.NotificationRepository

/**
 * Real SMS ingestion (1.2.0). Android 15 hides sensitive notification
 * content from listeners, but the system SMS store stays readable with
 * the user's READ_SMS permission — so SMS content comes from the store,
 * not from notifications. Inbox only, mirroring the incoming-only rule.
 */
object SmsSync {

    /** System-generated redaction texts — never real message content. */
    val PLACEHOLDERS = setOf(
        "Sensitive notification content hidden",
        "Đã ẩn nội dung thông báo nhạy cảm"
    )

    fun hasReadPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun defaultSmsPackage(ctx: Context): String? = try {
        Telephony.Sms.getDefaultSmsPackage(ctx)
    } catch (e: Exception) {
        null
    }

    /** (address, body, dateMillis) newest first; limit <= 0 means all. */
    private fun queryInbox(ctx: Context, limit: Int): List<Triple<String?, String, Long>> {
        val out = ArrayList<Triple<String?, String, Long>>()
        val proj = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val order = Telephony.Sms.DATE + " DESC" + if (limit > 0) " LIMIT $limit" else ""
        try {
            ctx.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, proj, null, null, order)
                ?.use { c ->
                    val ia = c.getColumnIndex(Telephony.Sms.ADDRESS)
                    val ib = c.getColumnIndex(Telephony.Sms.BODY)
                    val id = c.getColumnIndex(Telephony.Sms.DATE)
                    while (c.moveToNext()) {
                        val body = if (ib >= 0) c.getString(ib) else null
                        if (body.isNullOrEmpty()) continue
                        out.add(
                            Triple(
                                if (ia >= 0) c.getString(ia) else null,
                                body,
                                if (id >= 0) c.getLong(id) else 0L
                            )
                        )
                    }
                }
        } catch (e: Exception) {
            // permission missing or provider unavailable — stay silent
        }
        return out
    }

    private fun sync(ctx: Context, repo: NotificationRepository, limit: Int) {
        if (!hasReadPermission(ctx) || !CoreSettings.smsEnabled(ctx)) return
        val pkg = defaultSmsPackage(ctx) ?: return
        val items = queryInbox(ctx, limit)
        if (items.isNotEmpty()) repo.storeSmsMessages(pkg, items)
        repo.deletePlaceholderMessages(pkg, PLACEHOLDERS)
    }

    fun syncRecent(ctx: Context, repo: NotificationRepository) = sync(ctx, repo, 300)

    fun syncFull(ctx: Context, repo: NotificationRepository) = sync(ctx, repo, 0)
}
