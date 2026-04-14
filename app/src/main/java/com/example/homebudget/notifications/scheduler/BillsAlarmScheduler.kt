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
    private const val SLOT_WEEK_BEFORE = 1
    private const val SLOT_TWO_DAYS_BEFORE = 2
    private const val SLOT_ONE_DAY_BEFORE = 3
    private const val SLOT_DAY_OF = 4
    private const val SLOT_ONE_DAY_OVERDUE = 5
    private const val SLOT_TWO_DAYS_OVERDUE = 6
    private const val SLOT_THREE_DAYS_OVERDUE = 7

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
        for (slot in SLOT_WEEK_BEFORE..SLOT_THREE_DAYS_OVERDUE) {
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
        val oneDayOverdue = atFixedTime(dateMillis + DAY_MS, hour, minute)
        val twoDaysOverdue = atFixedTime(dateMillis + 2 * DAY_MS, hour, minute)
        val threeDaysOverdue = atFixedTime(dateMillis + 3 * DAY_MS, hour, minute)

        scheduleReminder(context, expenseId, weekBefore, SLOT_WEEK_BEFORE)
        scheduleReminder(context, expenseId, twoDaysBefore, SLOT_TWO_DAYS_BEFORE)
        scheduleReminder(context, expenseId, oneDayBefore, SLOT_ONE_DAY_BEFORE)
        scheduleReminder(context, expenseId, dayOf, SLOT_DAY_OF)
        scheduleReminder(context, expenseId, oneDayOverdue, SLOT_ONE_DAY_OVERDUE)
        scheduleReminder(context, expenseId, twoDaysOverdue, SLOT_TWO_DAYS_OVERDUE)
        scheduleReminder(context, expenseId, threeDaysOverdue, SLOT_THREE_DAYS_OVERDUE)
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
