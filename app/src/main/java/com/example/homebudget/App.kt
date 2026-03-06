package com.example.homebudget

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.homebudget.utils.settings.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        when (Prefs.getAppTheme(this)) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}