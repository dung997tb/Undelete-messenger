package com.dung.notifycore

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.dung.notifycore.contract.CoreContract
import com.dung.notifycore.contract.INotifyCallback
import com.dung.notifycore.contract.INotifyCoreService

/**
 * IPC surface for satellite apps.
 * Gated by the signature-level permission in the manifest; sensitive
 * operations re-check the caller signature permission defensively.
 */
class NotifyCoreService : Service() {

    private val binder = object : INotifyCoreService.Stub() {
        override fun getApiVersion(): Int = CoreContract.API_VERSION

        override fun getCapturedCount(): Long = CoreRuntime.repo(this@NotifyCoreService).count()

        override fun registerCallback(callback: INotifyCallback?) {
            if (callback != null) CoreRuntime.callbacks.register(callback)
        }

        override fun unregisterCallback(callback: INotifyCallback?) {
            if (callback != null) CoreRuntime.callbacks.unregister(callback)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
