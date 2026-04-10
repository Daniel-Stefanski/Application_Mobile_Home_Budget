package com.example.homebudget

import android.app.Application
import com.example.homebudget.utils.settings.Prefs
import com.example.homebudget.utils.settings.ThemeHelper

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeHelper.applySavedTheme(Prefs.getAppTheme(this))
    }
}
