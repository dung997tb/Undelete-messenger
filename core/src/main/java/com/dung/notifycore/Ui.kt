package com.dung.notifycore

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import android.widget.LinearLayout
import java.text.NumberFormat
import java.util.Calendar

object Ui {
    val ACCENT: Int = Color.rgb(0x0A, 0x84, 0xFF)
    val RED: Int = Color.rgb(0xFF, 0x3B, 0x30)
    val RED_DARK: Int = Color.rgb(0xB0, 0x00, 0x20)
    val GREEN: Int = Color.rgb(0x34, 0xC7, 0x59)

    private val AVATAR_COLORS = intArrayOf(
        0x0084FF, 0x0068FF, 0x2AABEE, 0x06C755, 0xEA4335,
        0xFF9500, 0xAF52DE, 0x5856D6, 0x00C7BE, 0xFF6482
    ).map { (0xFF shl 24) or it }.toIntArray()

    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density).toInt()

    fun isNight(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun bg(c: Context): Int = if (isNight(c)) Color.rgb(0x1C, 0x1C, 0x1E) else Color.rgb(0xF5, 0xF5, 0xF7)
    fun card(c: Context): Int = if (isNight(c)) Color.rgb(0x2C, 0x2C, 0x2E) else Color.WHITE
    fun text(c: Context): Int = if (isNight(c)) Color.rgb(0xF5, 0xF5, 0xF7) else Color.rgb(0x1D, 0x1D, 0x1F)
    fun sub(c: Context): Int = if (isNight(c)) Color.rgb(0x98, 0x98, 0x9F) else Color.rgb(0x86, 0x86, 0x8B)
    fun redBg(c: Context): Int = if (isNight(c)) Color.rgb(0x40, 0x23, 0x24) else Color.rgb(0xFF, 0xE9, 0xE8)
    fun warnBg(c: Context): Int = if (isNight(c)) Color.rgb(0x3D, 0x33, 0x14) else Color.rgb(0xFF, 0xF4, 0xD6)

    fun rounded(color: Int, radiusDp: Float, c: Context): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(c, radiusDp).toFloat()
        }

    fun statusBarHeight(c: Context): Int {
        val id = c.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) c.resources.getDimensionPixelSize(id) else dp(c, 24f)
    }

    fun fmtCount(n: Long): String = NumberFormat.getInstance().format(n)

    /** HH:mm for today, dd/MM HH:mm otherwise. */
    fun fmtWhen(t: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        val now = Calendar.getInstance()
        val hhmm = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) hhmm else
            String.format("%02d/%02d %s", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, hhmm)
    }

    fun appLabel(pm: PackageManager, pkg: String): String = try {
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    fun appIcon(pm: PackageManager, pkg: String): Drawable? = try {
        pm.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }

    fun avatarColor(pkg: String): Int =
        AVATAR_COLORS[(pkg.hashCode() and Int.MAX_VALUE) % AVATAR_COLORS.size]

    fun vspace(c: Context, hDp: Int): View = View(c).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, hDp.toFloat()))
    }

    fun circleBitmap(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val m = Matrix()
        m.setTranslate(-(src.width - size) / 2f, -(src.height - size) / 2f)
        shader.setLocalMatrix(m)
        paint.shader = shader
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return out
    }

    /** Round avatar image when available, colored initial letter otherwise. */
    fun avatarOrLetter(c: Context, bmp: Bitmap?, label: String, sizeDp: Float, seed: String): View {
        val px = dp(c, sizeDp)
        return if (bmp != null) {
            ImageView(c).apply {
                setImageBitmap(circleBitmap(bmp))
                layoutParams = LinearLayout.LayoutParams(px, px)
            }
        } else {
            TextView(c).apply {
                text = label.trim().take(1).uppercase().ifEmpty { "?" }
                textSize = sizeDp * 0.44f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = rounded(avatarColor(seed), sizeDp / 2f, c)
                layoutParams = LinearLayout.LayoutParams(px, px)
            }
        }
    }
}
