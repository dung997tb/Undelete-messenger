package com.dung.notifycore

import android.content.Context

/** Tiny state bridge so the management UI can show live listener status. */
object CoreStatus {
    private const val PREFS = "core_status"
    private const val K_CONNECTED = "listener_connected"

    fun setConnected(context: Context, connected: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(K_CONNECTED, connected).apply()
    }

    fun isConnected(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(K_CONNECTED, false)
}
