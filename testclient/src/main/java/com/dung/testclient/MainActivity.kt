package com.dung.testclient

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dung.notifycore.contract.CoreContract
import com.dung.notifycore.contract.INotifyCallback
import com.dung.notifycore.contract.INotifyCoreService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var log: TextView
    private val sb = StringBuilder()
    private val io = Executors.newSingleThreadExecutor()
    private var svc: INotifyCoreService? = null

    private val callback = object : INotifyCallback.Stub() {
        override fun onNotificationPosted(pkg: String?, title: String?, text: String?, postTime: Long) {
            append("LIVE: $pkg — $title: $text")
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = INotifyCoreService.Stub.asInterface(binder)
            svc = s
            append("CONNECTED to Core — API v${s.apiVersion}, count=${s.capturedCount}")
            s.registerCallback(callback)
            append("Callback registered — waiting for LIVE events…")
            queryProvider()
            queryMessages()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            append("DISCONNECTED")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply { textSize = 15f; setPadding(48, 320, 48, 48) }
        setContentView(log)
        append("TestClient starting…")
        try {
            val intent = Intent(CoreContract.SERVICE_ACTION).setPackage(CoreContract.CORE_PACKAGE)
            if (!bindService(intent, conn, BIND_AUTO_CREATE)) append("BIND REJECTED (returned false)")
        } catch (e: SecurityException) {
            append("BIND REJECTED: SecurityException")
        }
    }

    private fun queryProvider() {
        io.execute {
            try {
                val cur = contentResolver.query(
                    Uri.parse(CoreContract.PROVIDER_URI), null, null, null, null
                )
                if (cur == null) {
                    append("Provider: null cursor")
                    return@execute
                }
                cur.use {
                    val pi = it.getColumnIndex(CoreContract.COL_PACKAGE)
                    val ti = it.getColumnIndex(CoreContract.COL_TITLE)
                    val xi = it.getColumnIndex(CoreContract.COL_TEXT)
                    var first = ""
                    if (it.moveToFirst()) {
                        first = "${it.getString(pi)} — ${it.getString(ti)}: ${it.getString(xi)}"
                    }
                    append("Provider: ${it.count} rows; latest: $first")
                }
            } catch (e: SecurityException) {
                append("PROVIDER REJECTED: SecurityException")
            } catch (e: Exception) {
                append("PROVIDER ERROR: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun append(s: String) {
        runOnUiThread { sb.append(s).append("\n"); log.text = sb.toString() }
    }


    private fun queryMessages() {
        io.execute {
            try {
                val cur = contentResolver.query(
                    Uri.parse(CoreContract.PROVIDER_MESSAGES_URI), null, null, null, null
                )
                if (cur == null) {
                    append("Messages: null cursor")
                    return@execute
                }
                cur.use {
                    val si = it.getColumnIndex(CoreContract.COL_SENDER)
                    val xi = it.getColumnIndex(CoreContract.COL_TEXT)
                    val vi = it.getColumnIndex(CoreContract.COL_VANISHED_AT)
                    append("Messages: ${it.count} rows")
                    var shown = 0
                    while (it.moveToNext() && shown < 10) {
                        val van = if (it.isNull(vi)) "" else " [VANISHED]"
                        append("MSG: ${it.getString(si)}: ${it.getString(xi)}$van")
                        shown++
                    }
                }
            } catch (e: SecurityException) {
                append("MESSAGES REJECTED: SecurityException")
            } catch (e: Exception) {
                append("MESSAGES ERROR: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}
