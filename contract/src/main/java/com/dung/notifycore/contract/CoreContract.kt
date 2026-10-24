package com.dung.notifycore.contract

/**
 * Shared constants for the NotifyCore ecosystem.
 * Every satellite app depends on this contract module — and nothing else from Core.
 */
object CoreContract {
    const val CORE_PACKAGE = "com.dung.notifycore"
    const val API_VERSION = 1

    /** Signature-level permission guarding the bound IPC service. */
    const val PERMISSION_IPC = "com.dung.notifycore.permission.IPC"

    /** Separate signature-level permission guarding history reads via the provider. */
    const val PERMISSION_READ = "com.dung.notifycore.permission.READ_HISTORY"

    /** ContentProvider authority + content URI for captured-history queries. */
    const val PROVIDER_AUTHORITY = "com.dung.notifycore.provider"
    const val PROVIDER_URI = "content://com.dung.notifycore.provider/notifications"

    /** Per-message rows extracted from chat (MessagingStyle) notifications. */
    const val PROVIDER_MESSAGES_URI = "content://com.dung.notifycore.provider/messages"

    /** Intent action used to bind INotifyCoreService. */
    const val SERVICE_ACTION = "com.dung.notifycore.contract.BIND"

    // Provider cursor columns (mirror the Core DB schema v1)
    const val COL_KEY = "sbn_key"
    const val COL_PACKAGE = "package_name"
    const val COL_TITLE = "title"
    const val COL_TEXT = "text"
    const val COL_BIG_TEXT = "big_text"
    const val COL_CATEGORY = "category"
    const val COL_POST_TIME = "post_time"
    const val COL_RECEIVED_AT = "received_at"
    const val COL_UPDATED_AT = "updated_at"
    const val COL_UPDATE_COUNT = "update_count"
    const val COL_REMOVED_AT = "removed_at"

    // Message-row columns (provider /messages)
    const val COL_CONVERSATION = "conversation"
    const val COL_SENDER = "sender"
    const val COL_MSG_TIME = "msg_time"
    const val COL_VANISHED_AT = "vanished_at"
}
