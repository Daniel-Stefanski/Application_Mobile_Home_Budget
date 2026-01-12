package com.example.homebudget.notifications.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.utils.locale.LocaleUtils
import com.example.homebudget.utils.settings.Prefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

//BillsNotificationReceiver.kt – odbiera zaplanowane alarmy i wywołuje powiadomienia o rachunkach.
class BillsNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isNotificationsEnabled(context)) return
        val expenseId = intent.getIntExtra("expenseId", -1)
        val alarmSlot = intent.getIntExtra("alarmSlot", -1)
        if (expenseId == -1) return


        // pokaż powiadomienie - pobierz dane z DB, żeby zbudować treść
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val expense = db.expenseDao().getExpenseById(expenseId)
            if (expense == null) return@launch

            // jeśli status opłacony -> nic nie robimy
            if (expense.status == "opłacony") return@launch

            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val title = "Przypomnienie: ${expense.description ?: "Rachunek"}"
            val dateStr = sdf.format(Date(expense.date))
            val amountStr = String.format(LocaleUtils.POLISH, "%.2f", expense.amount)
            val text = "${expense.description ?: "-"} — $amountStr zł — termin: $dateStr"

            // Note: notyfikacja id używa expenseId*100 + slot, żeby mieć też unikalne id w systemie
            NotificationHelper.notify(context, expenseId * 100 + (alarmSlot.takeIf { it>0 } ?: 0), title, text)

            // jeśli to wydatek cykliczny -> zaplanuj następne przypomnienie
            if (expense.isRecurring && expense.repeatInterval > 0) {
                // anuluj stare (bezpiecznie)
                BillsAlarmScheduler.cancelAllReminders(context, expense.id)
                // Wylicz nową datę
                val cal = Calendar.getInstance().apply {
                    timeInMillis = expense.date
                    add(Calendar.MONTH, expense.repeatInterval)
                }
                val nextDate = cal.timeInMillis
                //zapisz do bazy
                db.expenseDao().updateDate(expense.id, nextDate)

                // Ustawiamy nowy alarm
                BillsAlarmScheduler.scheduleAllRemindersForDate(context, expense.id, nextDate)

            }
        }
    }
}