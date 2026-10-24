package com.dung.notifycore

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.dung.notifycore.contract.CoreContract

/**
 * Read-only window into captured history.
 * Manifest enforces PERMISSION_READ (signature level) for reads;
 * every write path below is deliberately a no-op/rejection.
 * Routes: /notifications (v1), /messages (suite 1.0.0, add-only).
 */
class NotifyProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(CoreContract.PROVIDER_AUTHORITY, "notifications", 1)
        addURI(CoreContract.PROVIDER_AUTHORITY, "messages", 2)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        val limit = uri.getQueryParameter("limit")?.toIntOrNull()?.coerceIn(1, 5000) ?: 200
        val repo = CoreRuntime.repo(context!!)
        return when (matcher.match(uri)) {
            2 -> repo.messagesCursor(limit)
            else -> repo.latestCursor(limit)
        }
    }

    override fun getType(uri: Uri): String = when (matcher.match(uri)) {
        2 -> "vnd.android.cursor.dir/vnd.com.dung.notifycore.messages"
        else -> "vnd.android.cursor.dir/vnd.com.dung.notifycore.notifications"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("NotifyCore history is read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
