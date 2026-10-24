package com.dung.notifycore

import android.content.Context
import android.os.RemoteCallbackList
import com.dung.notifycore.contract.INotifyCallback
import com.dung.notifycore.data.NotificationRepository

/** Process-wide hub shared by the listener, the IPC service and the provider. */
object CoreRuntime {
    @Volatile
    private var repo: NotificationRepository? = null

    val callbacks = RemoteCallbackList<INotifyCallback>()

    fun repo(context: Context): NotificationRepository =
        repo ?: synchronized(this) {
            repo ?: NotificationRepository(context.applicationContext).also { repo = it }
        }

    fun dispatchPosted(pkg: String, title: String?, text: String?, postTime: Long) {
        val n = callbacks.beginBroadcast()
        for (i in 0 until n) {
            try {
                callbacks.getBroadcastItem(i).onNotificationPosted(pkg, title, text, postTime)
            } catch (e: Exception) {
                // dead or misbehaving client: ignore, RemoteCallbackList cleans up
            }
        }
        callbacks.finishBroadcast()
    }
}
