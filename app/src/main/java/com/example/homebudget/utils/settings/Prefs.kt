package com.example.homebudget.utils.settings

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val KEY_SUPABASE_UID = "SUPABASE_UID"
    private const val KEY_PENDING_PASSWORD_RESET_EMAIL = "PENDING_PASSWORD_RESET_EMAIL"

    fun setSupabaseUid(context: Context, uid: String) {
        prefs(context).edit().putString(KEY_SUPABASE_UID, uid).apply()
    }

    fun getSupabaseUid(context: Context): String? {
        return prefs(context).getString(KEY_SUPABASE_UID, null)
    }

    fun setPendingPasswordResetEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_PENDING_PASSWORD_RESET_EMAIL, email).apply()
    }

    fun getPendingPasswordResetEmail(context: Context): String? {
        return prefs(context).getString(KEY_PENDING_PASSWORD_RESET_EMAIL, null)
    }

    fun clearPendingPasswordResetEmail(context: Context) {
        prefs(context).edit().remove(KEY_PENDING_PASSWORD_RESET_EMAIL).apply()
    }

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

    fun clearSession(context: Context) {
        prefs(context).edit()
            .remove(USER_ID)
            .remove(KEY_SUPABASE_UID)
            .remove(REMEMBER_ME)
            .apply()
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
    private const val APP_THEME = "APP_THEME"

    private fun themeKey(userId: Int): String = "${APP_THEME}_$userId"

    fun getAppTheme(context: Context): String {
        val userId = getUserId(context)
        return if (userId != -1) {
            getAppThemeForUser(context, userId)
        } else {
            prefs(context).getString(APP_THEME, "light") ?: "light"
        }
    }

    fun getAppThemeForUser(context: Context, userId: Int): String =
        prefs(context).getString(themeKey(userId), "light") ?: "light"

    fun setAppTheme(context: Context, theme: String) {
        val userId = getUserId(context)
        if (userId != -1) {
            setAppThemeForUser(context, userId, theme)
        } else {
            prefs(context).edit().putString(APP_THEME, theme).apply()
        }
    }

    fun setAppThemeForUser(context: Context, userId: Int, theme: String) {
        prefs(context).edit().putString(themeKey(userId), theme).apply()
    }

    fun clearAppThemeForUser(context: Context, userId: Int) {
        prefs(context).edit().remove(themeKey(userId)).apply()
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
