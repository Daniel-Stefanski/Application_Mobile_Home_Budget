package com.example.homebudget.notifications.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.utils.locale.LocaleUtils
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BillsNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isNotificationsEnabled(context)) return

        val expenseId = intent.getIntExtra("expenseId", -1)
        val alarmSlot = intent.getIntExtra("alarmSlot", -1)
        if (expenseId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val expense = db.expenseDao().getExpenseById(expenseId) ?: return@launch

                if (isPaidStatus(expense.status)) return@launch

                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val isOverdueReminder = alarmSlot in 5..7
                val title = if (isOverdueReminder) {
                    "Przypomnienie o zaległym terminie płatności"
                } else {
                    "Przypomnienie o terminie płatności"
                }
                val dateStr = sdf.format(Date(expense.date))
                val amountStr = String.format(LocaleUtils.POLISH, "%.2f", expense.amount)
                val billName = expense.description?.takeIf { it.isNotBlank() } ?: "Rachunek"
                val text = if (isOverdueReminder) {
                    "$billName • $amountStr zl • Termin płatności: $dateStr. Opłać rachunek i oznacz go jako opłacony."
                } else {
                    "$billName • $amountStr zl • Termin płatności: $dateStr"
                }

                NotificationHelper.createNotificationChannel(context)
                NotificationHelper.notify(
                    context,
                    expenseId * 100 + (alarmSlot.takeIf { it > 0 } ?: 0),
                    title,
                    text
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isPaidStatus(status: String): Boolean {
        return status.trim().lowercase().startsWith("op")
    }
}
