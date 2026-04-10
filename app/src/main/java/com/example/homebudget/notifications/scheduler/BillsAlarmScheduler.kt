package com.example.homebudget.notifications.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.homebudget.notifications.receiver.BillsNotificationReceiver
import com.example.homebudget.utils.settings.Prefs
import java.util.Calendar

object BillsAlarmScheduler {
    const val DAY_MS = 24L * 60 * 60 * 1000

    private fun pendingIntentFor(context: Context, expenseId: Int, slot: Int): PendingIntent {
        val intent = Intent(context, BillsNotificationReceiver::class.java).apply {
            putExtra("expenseId", expenseId)
            putExtra("alarmSlot", slot)
        }
        val requestCode = expenseId * 10 + slot
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    fun scheduleReminder(context: Context, expenseId: Int, triggerAtMillis: Long, slot: Int) {
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context, expenseId, slot)

        try {
            val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canUseExactAlarm) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else if (canUseExactAlarm) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
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
        for (slot in 1..5) {
            cancelReminder(context, expenseId, slot)
        }
    }

    fun scheduleAllRemindersForDate(context: Context, expenseId: Int, dateMillis: Long) {
        if (!Prefs.isNotificationsEnabled(context)) return

        val hour = 8
        val minute = 0

        val weekBefore = atFixedTime(dateMillis - 7 * DAY_MS, hour, minute)
        val twoDaysBefore = atFixedTime(dateMillis - 2 * DAY_MS, hour, minute)
        val oneDayBefore = atFixedTime(dateMillis - DAY_MS, hour, minute)
        val dayOf = atFixedTime(dateMillis, hour, minute)

        scheduleReminder(context, expenseId, weekBefore, 1)
        scheduleReminder(context, expenseId, twoDaysBefore, 2)
        scheduleReminder(context, expenseId, oneDayBefore, 3)
        scheduleReminder(context, expenseId, dayOf, 4)

        if (System.currentTimeMillis() > dayOf) {
            scheduleNextOverdueReminder(context, expenseId, dateMillis)
        }
    }

    fun scheduleNextOverdueReminder(context: Context, expenseId: Int, dueDateMillis: Long) {
        if (!Prefs.isNotificationsEnabled(context)) return

        val hour = 8
        val minute = 0
        var triggerAtMillis = atFixedTime(dueDateMillis + DAY_MS, hour, minute)

        while (triggerAtMillis <= System.currentTimeMillis()) {
            triggerAtMillis += DAY_MS
        }

        scheduleReminder(context, expenseId, triggerAtMillis, 5)
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
