package com.dung.testsender

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person

/**
 * Fake notification source.
 * Set A (plain): chat + bank OTP + balance, then an edit-update.
 * Set B (MessagingStyle): 1-1 chat where message 2 arrives, then is
 * RECALLED (removed from the bundle while the notification lives on).
 */
class MainActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            textSize = 16f
            setPadding(48, 320, 48, 48)
            text = "TestSender: chuan bi gui…"
        }
        setContentView(status)
        createChannels()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            postSet()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        postSet()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("chat", "Chat", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel("bank", "Bank", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun post(channel: String, id: Int, title: String, text: String, category: String?) {
        val b = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        if (category != null) b.setCategory(category)
        NotificationManagerCompat.from(this).notify(id, b.build())
    }

    private fun postChat(id: Int, msgs: List<Triple<String, String, Long>>) {
        val me = Person.Builder().setName("Ban").build()
        val style = NotificationCompat.MessagingStyle(me)
        for ((senderName, text, t) in msgs) {
            val p = Person.Builder().setName(senderName).build()
            style.addMessage(text, t, p)
        }
        val b = NotificationCompat.Builder(this, "chat")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        NotificationManagerCompat.from(this).notify(id, b.build())
    }


    private fun makeAvatarBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(0x00, 0x7A, 0x5F))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("S", 48f, 66f, paint)
        return bmp
    }

    private fun postChatAvatar(id: Int, msgs: List<Triple<String, String, Long>>, iconBmp: Bitmap) {
        val me = Person.Builder().setName("Ban").build()
        val style = NotificationCompat.MessagingStyle(me)
        style.setConversationTitle("Shop X")
        for ((senderName, text, t) in msgs) {
            val p = Person.Builder().setName(senderName)
                .setIcon(IconCompat.createWithBitmap(iconBmp)).build()
            style.addMessage(text, t, p)
        }
        val b = NotificationCompat.Builder(this, "chat")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setLargeIcon(iconBmp)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        NotificationManagerCompat.from(this).notify(id, b.build())
    }

    private fun postSet() {
        status.text = "TestSender: dang gui…"
        val base = System.currentTimeMillis()
        val m1 = Triple("Nga Trần", "Em an com chua?", base)
        val m2 = Triple("Nga Trần", "Nho uong nuoc nha", base + 60_000)
        val m3 = Triple("Nga Trần", "Tin nay la tin moi nhat", base + 120_000)

        // Set A — plain notifications
        handler.postDelayed({
            post("chat", 101, "Nga Trần", "Anh ăn cơm chưa?", NotificationCompat.CATEGORY_MESSAGE)
        }, 500)
        handler.postDelayed({
            post(
                "bank", 102, "Vietcombank",
                "Ma OTP cua quy khach la 483920. Ma co hieu luc 3 phut. " +
                    "Khong chia se ma nay cho bat ky ai.", null
            )
        }, 1200)
        handler.postDelayed({
            post(
                "bank", 103, "Vietcombank",
                "So du TK 007100xxxx thay doi +5.000.000 VND. So du hien tai: 52.300.000 VND.", null
            )
        }, 1900)
        handler.postDelayed({
            post(
                "chat", 101, "Nga Trần",
                "Anh ăn cơm chưa? Nhớ ăn đúng giờ nha emm", NotificationCompat.CATEGORY_MESSAGE
            )
        }, 3600)

        // Set B — MessagingStyle chat with a recall of m2
        handler.postDelayed({ postChat(201, listOf(m1)) }, 800)
        handler.postDelayed({ postChat(201, listOf(m1, m2)) }, 2800)
        handler.postDelayed({
            postChat(201, listOf(m1, m3))
            status.text = "TestSender: da gui xong — m2 cua chat 201 da bi THU HOI"
        }, 4800)

        // Set C — 1.0.2 scenarios on chat 301, deterministic timestamps
        val avatarBmp = makeAvatarBitmap()
        val t1 = base + 3_600_000L
        val t2 = t1 + 60_000
        val t3 = t1 + 120_000
        val t4 = t1 + 180_000
        val a1 = Triple("Shop X", "gia 100", t1)
        val a1e = Triple("Shop X", "gia 200", t1)
        val a2 = Triple("Shop X", "con hang", t2)
        val a3 = Triple("Shop X", "ok em", t3)
        val a4 = Triple("Shop X", "hang moi ve", t4)
        handler.postDelayed({ postChatAvatar(301, listOf(a1, a2, a3), avatarBmp) }, 5000)
        handler.postDelayed({ postChatAvatar(301, listOf(a1e, a2, a3), avatarBmp) }, 7500)
        handler.postDelayed({ postChatAvatar(301, listOf(a2, a3, a4), avatarBmp) }, 10000)
        handler.postDelayed({ postChatAvatar(301, listOf(a2, a4), avatarBmp) }, 12500)
        handler.postDelayed({
            NotificationManagerCompat.from(this).cancel(301)
            status.text = "TestSender: set C xong (301 da cancel)"
        }, 15000)

        // Set D \u2014 a redaction placeholder must NOT be stored as a message (1.2.0)
        handler.postDelayed({
            postChat(401, listOf(Triple("Bank X", "Sensitive notification content hidden", base + 7_200_000L)))
            status.text = "TestSender: set D xong"
        }, 17000)
    }
}
