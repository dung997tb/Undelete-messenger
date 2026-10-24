package com.dung.notifycore

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class NotifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        when (CoreSettings.darkMode(this)) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
