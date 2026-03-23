package com.example.homebudget.notifications.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.utils.locale.LocaleUtils
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
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

                if (expense.status == "opłacony") return@launch

                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val title = "Przypomnienie: ${expense.description ?: "Rachunek"}"
                val dateStr = sdf.format(Date(expense.date))
                val amountStr = String.format(LocaleUtils.POLISH, "%.2f", expense.amount)
                val text = "${expense.description ?: "-"} - $amountStr zl - termin: $dateStr"

                NotificationHelper.createNotificationChannel(context)
                NotificationHelper.notify(
                    context,
                    expenseId * 100 + (alarmSlot.takeIf { it > 0 } ?: 0),
                    title,
                    text
                )

                if (expense.isRecurring && expense.repeatInterval > 0 && alarmSlot == 3) {
                    BillsAlarmScheduler.cancelAllReminders(context, expense.id)

                    val cal = Calendar.getInstance().apply {
                        timeInMillis = expense.date
                        add(Calendar.MONTH, expense.repeatInterval)
                    }
                    val nextDate = cal.timeInMillis

                    db.expenseDao().updateDate(expense.id, nextDate)
                    BillsAlarmScheduler.scheduleAllRemindersForDate(context, expense.id, nextDate)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
