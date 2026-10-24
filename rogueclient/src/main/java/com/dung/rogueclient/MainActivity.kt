package com.dung.rogueclient

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.dung.notifycore.contract.CoreContract
import com.dung.notifycore.contract.INotifyCoreService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var log: TextView
    private val sb = StringBuilder()
    private val io = Executors.newSingleThreadExecutor()

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = INotifyCoreService.Stub.asInterface(binder)
            append("RogueClient UNEXPECTEDLY CONNECTED — API v${s.apiVersion}")
        }

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply { textSize = 15f; setPadding(48, 320, 48, 48) }
        setContentView(log)
        append("RogueClient starting (foreign signature)…")
        try {
            val intent = Intent(CoreContract.SERVICE_ACTION).setPackage(CoreContract.CORE_PACKAGE)
            if (!bindService(intent, conn, BIND_AUTO_CREATE)) append("BIND REJECTED (returned false)")
        } catch (e: SecurityException) {
            append("BIND REJECTED: SecurityException")
        }
        io.execute {
            try {
                val cur = contentResolver.query(
                    Uri.parse(CoreContract.PROVIDER_URI), null, null, null, null
                )
                if (cur == null) append("PROVIDER: null cursor (unexpected)")
                else cur.use { append("PROVIDER UNEXPECTEDLY OPEN: ${it.count} rows") }
            } catch (e: SecurityException) {
                append("PROVIDER REJECTED: SecurityException")
            } catch (e: Exception) {
                append("PROVIDER ERROR: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun append(s: String) {
        runOnUiThread { sb.append(s).append("\n"); log.text = sb.toString() }
    }
}
