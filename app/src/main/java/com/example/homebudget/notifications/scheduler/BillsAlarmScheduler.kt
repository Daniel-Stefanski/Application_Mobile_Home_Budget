package com.example.homebudget.notifications.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.notifications.receiver.BillsNotificationReceiver
import com.example.homebudget.utils.settings.Prefs
import java.util.Calendar

//BillsAlarmScheduler.kt – planuje i anuluje alarmy dla przypomnień o rachunkach.
object BillsAlarmScheduler {
    const val DAY_MS = 24L * 60 * 60 * 1000
    private fun pendingIntentFor(context: Context, expenseId: Int, slot: Int): PendingIntent {
        //slot: 1 = -48h, 2 = -24h, 3 = dayof
        val intent = Intent(context, BillsNotificationReceiver::class.java).apply {
            putExtra("expenseId", expenseId)
            putExtra("alarmSlot", slot)
        }
        val requestCode = expenseId * 10 + slot
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    fun scheduleReminder(context: Context, expenseId: Int, triggerAtMillis: Long, slot: Int) {
        if (triggerAtMillis <= System.currentTimeMillis()) return // nie planujemy w przeszłości

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context, expenseId, slot)

        // używamy setExactAndAllowWhileIdle jeśli dostępne
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(AlarmManager::class.java)
                if (!am.canScheduleExactAlarms()) {
                    // użytkownik musi włączyć „Dokładne alarmy”
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelReminder(context: Context, expenseId: Int, slot: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context, expenseId, slot)
        alarmManager.cancel(pi)
        pi.cancel()
    }

    fun cancelAllReminders(context: Context, expenseId: Int) {
        //try sloty
        cancelReminder(context, expenseId, 1)
        cancelReminder(context, expenseId, 2)
        cancelReminder(context, expenseId, 3)
    }

    fun scheduleAllRemindersForDate(context: Context, expenseId: Int, dateMillis: Long) {
        if (!Prefs.isNotificationsEnabled(context)) return

        //Ustawiamy stałą godzinę powiadomienia: 08:00
        val hour = 8
        val minute = 0

        //dateMillis = termin płatności (uzytkownik) - zachowujemy ten timestamp
        val twoDaysBefore = atFixedTime(dateMillis - 2 * DAY_MS, hour, minute)
        val oneDayBefore = atFixedTime(dateMillis - 1 * DAY_MS, hour, minute)
        val dayOf = atFixedTime(dateMillis, hour, minute)

        // slot 1 = -48h, 2 = -24h, 3 = dayOf
        scheduleReminder(context, expenseId, twoDaysBefore, 1)
        scheduleReminder(context, expenseId, oneDayBefore, 2)
        scheduleReminder(context, expenseId, dayOf, 3)
    }

    fun scheduleAllRemindersForNextCycle(context: Context, expense: Expense) {
        //Oblicz następną datę (dodaj repeatInterval miesięcy do expense.date)
        val cal = Calendar.getInstance()
        cal.timeInMillis = expense.date
        cal.add(Calendar.MONTH, expense.repeatInterval)
        val nextDate = cal.timeInMillis

        scheduleAllRemindersForDate(context, expense.id, nextDate)
    }

    private fun atFixedTime(originalMillis: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = originalMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}