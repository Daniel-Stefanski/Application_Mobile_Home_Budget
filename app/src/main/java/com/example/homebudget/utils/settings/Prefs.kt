package com.example.homebudget.utils.settings

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val PREF_NAME = "HomeBudgetPrefs"
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Użytkownik
    const val USER_ID: String = "USER_ID"
    const val NOTIFICATIONS_ENABLED: String = "NOTIFICATIONS_ENABLED"

    fun getUserId(context: Context): Int =
        prefs(context).getInt(USER_ID, -1)

    fun setUserId(context: Context, value: Int) {
        prefs(context).edit().putInt(USER_ID, value).apply()
    }

    fun clearUserData(context: Context) {
        prefs(context).edit().remove(USER_ID).apply()
    }

    // Zapamietaj mnie
    private const val REMEMBER_ME = "rememberMe"

    fun isRememberMeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(REMEMBER_ME, false)

    fun setRememberMe(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(REMEMBER_ME, value).apply()
    }
    // Powiadomienia
    fun isNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(NOTIFICATIONS_ENABLED, true)

    fun setNotificationsEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(NOTIFICATIONS_ENABLED, value).apply()
    }

    // Motyw aplikacji
    private const val  APP_THEME = "APP_THEME" // "light", "night", "system"
    fun getAppTheme(context: Context): String =
        prefs(context).getString(APP_THEME, "system") ?: "system"

    fun setAppTheme(context: Context, theme: String) {
        prefs(context).edit().putString(APP_THEME, theme).apply()
    }

    // Ostatni wybrany miesiąc / rok w dashboard
    private const val LAST_MONTH = "LAST_MONTH"
    private const val LAST_YEAR = "LAST_YEAR"

    fun getLastMonth(context: Context): Int =
        prefs(context).getInt(LAST_MONTH, -1)

    fun setLastMonth(context: Context, value: Int) {
        prefs(context).edit().putInt(LAST_MONTH, value).apply()
    }

    fun getLastYear(context: Context): Int =
        prefs(context).getInt(LAST_YEAR, -1)

    fun setLastYear(context: Context, value: Int) {
        prefs(context).edit().putInt(LAST_YEAR, value).apply()
    }

    // Domyślna kategoria
    private const val DEFAULT_CATEGORY = "DEFAULT_CATEGORY"

    fun getDefaultCategory(context: Context): String =
        prefs(context).getString(DEFAULT_CATEGORY, "Brak") ?: "Brak"

    fun setDefaultCategory(context: Context, value: String) {
        prefs(context).edit().putString(DEFAULT_CATEGORY, value).apply()
    }

    // Domyślna metoda płatności
    private const val DEFAULT_PAYMENT = "DEFAULT_PAYMENT"

    fun getDefaultPayment(context: Context): String =
        prefs(context).getString(DEFAULT_PAYMENT, "Brak") ?: "Brak"

    fun setDefaultPayment(context: Context, value: String) {
        prefs(context).edit().putString(DEFAULT_PAYMENT, value).apply()
    }

    // Ostrzeżenie budżetu(dashboard)
    private const val LAST_80_WARNING_DATE = "LAST_80_WARNING_DATE"
    private const val LAST_100_WARNING_DATE = "LAST_100_WARNING_DATE"

    fun getLast80WarningDate(context: Context): String? =
        prefs(context).getString(LAST_80_WARNING_DATE, null)

    fun setLast80WarningDate(context: Context, value: String) {
        prefs(context).edit().putString(LAST_80_WARNING_DATE, value).apply()
    }

    fun getLast100WarningDate(context: Context): String? =
        prefs(context).getString(LAST_100_WARNING_DATE, null)

    fun setLast100WarningDate(context: Context, value: String) {
        prefs(context).edit().putString(LAST_100_WARNING_DATE, value).apply()
    }

    // Czyszczenie kolorów(dashboard)
    private const val LAST_COLOR_CLEANUP = "LAST_COLOR_CLEANUP"

    fun getLastColorCleanup(context: Context): Long =
        prefs(context).getLong(LAST_COLOR_CLEANUP, 0L)

    fun setLastColorCleanup(context: Context, timestamp: Long) {
        prefs(context).edit().putLong(LAST_COLOR_CLEANUP, timestamp).apply()
    }

    // Reset wszystkich ustawień
    fun resetAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}