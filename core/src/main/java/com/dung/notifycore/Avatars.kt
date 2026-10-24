package com.dung.notifycore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Sender/conversation avatars harvested from notifications (1.0.2).
 * Stored as small PNG files — deliberately NOT in the encrypted DB, so
 * no schema change; file names are SHA-256 hashes of package + name.
 */
object Avatars {
    private const val MAX_PX = 96

    private fun dir(ctx: Context): File =
        File(ctx.filesDir, "avatars").apply { if (!exists()) mkdirs() }

    private fun file(ctx: Context, pkg: String, kind: String, name: String): File {
        val raw = "$pkg|$kind|$name"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir(ctx), hash.substring(0, 32) + ".png")
    }

    fun senderFile(ctx: Context, pkg: String, sender: String): File = file(ctx, pkg, "s", sender)

    fun convFile(ctx: Context, pkg: String, conv: String): File = file(ctx, pkg, "c", conv)

    fun save(ctx: Context, target: File, bmp: Bitmap) {
        try {
            val out = if (bmp.width > MAX_PX || bmp.height > MAX_PX)
                Bitmap.createScaledBitmap(bmp, MAX_PX, MAX_PX, true) else bmp
            FileOutputStream(target).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            // avatar is decoration — never fail capture over it
        }
    }

    private val cache = HashMap<String, Pair<Long, Bitmap>>()

    @Synchronized
    fun loadCached(target: File): Bitmap? {
        if (!target.exists()) return null
        val key = target.absolutePath
        val mod = target.lastModified()
        cache[key]?.let { if (it.first == mod) return it.second }
        val bmp = try { BitmapFactory.decodeFile(key) } catch (e: Exception) { null } ?: return null
        if (cache.size > 64) cache.clear()
        cache[key] = mod to bmp
        return bmp
    }

    fun clearAll(ctx: Context) {
        try {
            dir(ctx).listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
        }
        synchronized(this) { cache.clear() }
    }

    fun bitmapFromIcon(ctx: Context, icon: Icon?): Bitmap? {
        val d = try { icon?.loadDrawable(ctx) } catch (e: Exception) { null } ?: return null
        return bitmapFromDrawable(d)
    }

    fun bitmapFromDrawable(d: Drawable): Bitmap? {
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth.coerceAtMost(192) else MAX_PX
        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight.coerceAtMost(192) else MAX_PX
        return try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, w, h)
            d.draw(canvas)
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
