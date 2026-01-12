package com.example.homebudget.notifications.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// SavingsGoalNotificationReceiver.kt – odbiera alarmy i pokazuje powiadomienia o celach.
class SavingsGoalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isNotificationsEnabled(context)) return
        val goalId = intent.getIntExtra("goalId", -1)
        val slot = intent.getIntExtra("slot", -1)
        if (goalId == -1) return
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val goal = db.savingsGoalDao().getGoalById(goalId) ?: return@launch
            val endDate = goal.endDate ?: return@launch
            // Jeśli cel osiągnięty - nie wysyłamy powiadomienia
            if (goal.savedAmount >= goal.targetAmount) return@launch
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL"))
            val dateStr = sdf.format(Date(endDate))
            val progress = if (goal.targetAmount > 0) {
                ((goal.savedAmount / goal.targetAmount) * 100).toInt().coerceIn(0, 100)
            } else 0

            val daysText = when (slot) {
                1 -> "miesiąc"
                2 -> "2 tygodnie"
                3 -> "1 tydzień"
                4 -> "2 dni"
                5 -> "1 dzień"
                6 -> "dzisiaj"
                else -> null
            }
            val title = goal.title
            val text = if (daysText != null)
                "Termin: $dateStr ($daysText). Postęp: $progress%."
            else
                "Termin: $dateStr. Postęp: $progress%."
            // Unikalne ID powiadomienia (jak przy rachunkach)
            val notificationId = goalId * 100 + (if (slot > 0) slot else 0)
            NotificationHelper.notifySavings(context, notificationId, title, text)
        }
    }
}