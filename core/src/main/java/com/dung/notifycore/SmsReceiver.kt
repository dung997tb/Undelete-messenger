package com.dung.notifycore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.concurrent.Executors

/** Wake-up on incoming SMS: pull the fresh content from the SMS store. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val appCtx = context.applicationContext
        val pending = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                SmsSync.syncRecent(appCtx, CoreRuntime.repo(appCtx))
            } catch (e: Exception) {
                // best effort — the next app open syncs again
            } finally {
                pending.finish()
            }
        }
    }
}
