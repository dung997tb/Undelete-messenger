package com.dung.notifycore

import android.content.Context

/**
 * User-facing Core settings (Unseen-style): blacklist + retention caps.
 * Retention policy (Dung, 2026-10-22): messaging apps are UNLIMITED by
 * default (anti-recall needs the full history); every other app is
 * capped at 100 stored messages. Both the chat classification and the
 * cap can be overridden per app.
 */
object CoreSettings {
    private const val PREFS = "core_settings"
    private const val K_BLACKLIST = "blacklist_pkgs"
    private const val K_DARK = "dark_mode"
    private const val K_CHAT_MARKED = "chat_marked_pkgs"
    private const val K_CAP_OVERRIDES = "cap_overrides" // entries "pkg=cap"

    const val UNLIMITED = -1
    const val DEFAULT_CAP_NORMAL = 100

    /** Well-known messaging apps — unlimited history by default. */
    val BUILTIN_CHAT_APPS = setOf(
        "com.facebook.orca",          // Messenger
        "com.facebook.mlite",
        "com.zing.zalo",              // Zalo
        "org.telegram.messenger",     // Telegram
        "org.telegram.messenger.web",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "jp.naver.line.android",      // LINE
        "com.viber.voip",
        "org.thoughtcrime.securesms", // Signal
        "com.kakao.talk",             // KakaoTalk
        "com.discord",
        "com.tencent.mm",             // WeChat
        "com.skype.raider",
        "com.Slack"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- blacklist ----
    fun blacklist(context: Context): Set<String> =
        prefs(context).getStringSet(K_BLACKLIST, emptySet()) ?: emptySet()

    fun setBlacklist(context: Context, pkgs: Set<String>) {
        prefs(context).edit().putStringSet(K_BLACKLIST, pkgs).apply()
    }

    fun isBlacklisted(context: Context, pkg: String): Boolean =
        blacklist(context).contains(pkg)

    // ---- dark mode: 0 system, 1 light, 2 dark ----
    fun darkMode(context: Context): Int = prefs(context).getInt(K_DARK, 0)

    fun setDarkMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(K_DARK, mode).apply()
    }

    // ---- chat classification ----
    fun isChatApp(context: Context, pkg: String): Boolean =
        pkg in BUILTIN_CHAT_APPS ||
            pkg == SmsSync.defaultSmsPackage(context) ||
            (prefs(context).getStringSet(K_CHAT_MARKED, emptySet()) ?: emptySet()).contains(pkg)

    fun setChatApp(context: Context, pkg: String, chat: Boolean) {
        val cur = HashSet(prefs(context).getStringSet(K_CHAT_MARKED, emptySet()) ?: emptySet())
        if (chat) cur.add(pkg) else cur.remove(pkg)
        prefs(context).edit().putStringSet(K_CHAT_MARKED, cur).apply()
    }

    // ---- per-app cap overrides ----
    fun capOverride(context: Context, pkg: String): Int? {
        val raw = prefs(context).getStringSet(K_CAP_OVERRIDES, emptySet()) ?: emptySet()
        for (e in raw) {
            val i = e.lastIndexOf('=')
            if (i > 0 && e.substring(0, i) == pkg) return e.substring(i + 1).toIntOrNull()
        }
        return null
    }

    fun setCapOverride(context: Context, pkg: String, cap: Int?) {
        val cur = HashSet(prefs(context).getStringSet(K_CAP_OVERRIDES, emptySet()) ?: emptySet())
        cur.removeAll { it.substringBeforeLast('=') == pkg }
        if (cap != null) cur.add("$pkg=$cap")
        prefs(context).edit().putStringSet(K_CAP_OVERRIDES, cur).apply()
    }

    /** Effective stored-message cap for an app. UNLIMITED (-1) = keep all. */
    fun capFor(context: Context, pkg: String): Int {
        capOverride(context, pkg)?.let { return it }
        return if (isChatApp(context, pkg)) UNLIMITED else DEFAULT_CAP_NORMAL
    }

    // ---- pins (1.1.0) ----
    private const val KEY_PIN_APPS = "pinned_apps"
    private const val KEY_PIN_CONVS = "pinned_convs"

    fun pinnedApps(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_PIN_APPS, emptySet())?.toSet() ?: emptySet()

    fun isAppPinned(ctx: Context, pkg: String): Boolean = pkg in pinnedApps(ctx)

    fun setAppPinned(ctx: Context, pkg: String, pinned: Boolean) {
        val cur = HashSet(pinnedApps(ctx))
        if (pinned) cur.add(pkg) else cur.remove(pkg)
        prefs(ctx).edit().putStringSet(KEY_PIN_APPS, cur).apply()
    }

    private fun convPinKey(pkg: String, conv: String?): String = pkg + "\u0001" + (conv ?: "")

    fun isConvPinned(ctx: Context, pkg: String, conv: String?): Boolean =
        (prefs(ctx).getStringSet(KEY_PIN_CONVS, emptySet()) ?: emptySet()).contains(convPinKey(pkg, conv))

    fun setConvPinned(ctx: Context, pkg: String, conv: String?, pinned: Boolean) {
        val cur = HashSet(prefs(ctx).getStringSet(KEY_PIN_CONVS, emptySet()) ?: emptySet())
        val k = convPinKey(pkg, conv)
        if (pinned) cur.add(k) else cur.remove(k)
        prefs(ctx).edit().putStringSet(KEY_PIN_CONVS, cur).apply()
    }

    // ---- onboarding (1.3.3) ----
    private const val K_ONBOARDING = "onboarding_done"

    fun onboardingDone(ctx: Context): Boolean = prefs(ctx).getBoolean(K_ONBOARDING, false)

    fun setOnboardingDone(ctx: Context, done: Boolean) {
        prefs(ctx).edit().putBoolean(K_ONBOARDING, done).apply()
    }

}
