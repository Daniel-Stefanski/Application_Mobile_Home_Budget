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

class SavingsGoalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isNotificationsEnabled(context)) return

        val goalId = intent.getIntExtra("goalId", -1)
        val slot = intent.getIntExtra("slot", -1)
        if (goalId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val goal = db.savingsGoalDao().getGoalById(goalId) ?: return@launch
                val endDate = goal.endDate ?: return@launch
                if (goal.savedAmount >= goal.targetAmount) return@launch

                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("pl-PL"))
                val dateStr = sdf.format(Date(endDate))
                val progress = if (goal.targetAmount > 0) {
                    ((goal.savedAmount / goal.targetAmount) * 100).toInt().coerceIn(0, 100)
                } else {
                    0
                }

                val daysText = when (slot) {
                    1 -> "za 30 dni"
                    2 -> "za 14 dni"
                    3 -> "za 7 dni"
                    4 -> "za 2 dni"
                    5 -> "za 1 dzień"
                    6 -> "dzisiaj"
                    else -> null
                }
                val savedAmount = String.format(Locale.forLanguageTag("pl-PL"), "%.2f", goal.savedAmount)
                val targetAmount = String.format(Locale.forLanguageTag("pl-PL"), "%.2f", goal.targetAmount)
                val title = "Zbliża się termin celu oszczędnościowego"
                val text = if (daysText != null) {
                    "${goal.title} • $savedAmount zł / $targetAmount zł • Termin: $dateStr ($daysText) • Postęp: $progress%"
                } else {
                    "${goal.title} • $savedAmount zł / $targetAmount zł • Termin: $dateStr • Postęp: $progress%"
                }

                NotificationHelper.createNotificationChannel(context)
                val notificationId = goalId * 100 + (if (slot > 0) slot else 0)
                NotificationHelper.notifySavings(context, notificationId, title, text)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

