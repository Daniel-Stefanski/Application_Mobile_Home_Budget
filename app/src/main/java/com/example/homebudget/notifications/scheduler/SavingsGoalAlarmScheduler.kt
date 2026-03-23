package com.example.homebudget.notifications.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.homebudget.data.entity.SavingsGoal
import com.example.homebudget.notifications.receiver.SavingsGoalNotificationReceiver
import com.example.homebudget.utils.settings.Prefs
import java.util.Calendar

object SavingsGoalAlarmScheduler {
    private fun pendingIntentFor(context: Context, goalId: Int, slot: Int): PendingIntent {
        val intent = Intent(context, SavingsGoalNotificationReceiver::class.java).apply {
            putExtra("goalId", goalId)
            putExtra("slot", slot)
        }
        val requestCode = goalId * 10 + slot
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun scheduleReminder(context: Context, goalId: Int, triggerAtMillis: Long, slot: Int) {
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context, goalId, slot)
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

    fun cancelReminder(context: Context, goalId: Int, slot: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context, goalId, slot)
        alarmManager.cancel(pi)
        pi.cancel()
    }

    fun cancelAllReminders(context: Context, goalId: Int) {
        for (slot in 1..6) {
            cancelReminder(context, goalId, slot)
        }
    }

    fun scheduleAllRemindersForGoal(context: Context, goal: SavingsGoal) {
        if (!Prefs.isNotificationsEnabled(context)) return

        val endDate = goal.endDate ?: return
        if (goal.savedAmount >= goal.targetAmount) return

        val hour = 8
        val minute = 0
        val dayMs = 24L * 60 * 60 * 1000

        val minus30 = atFixedTime(endDate - 30L * dayMs, hour, minute)
        val minus14 = atFixedTime(endDate - 14L * dayMs, hour, minute)
        val minus7 = atFixedTime(endDate - 7L * dayMs, hour, minute)
        val minus2 = atFixedTime(endDate - 2L * dayMs, hour, minute)
        val minus1 = atFixedTime(endDate - 1L * dayMs, hour, minute)
        val dayOf = atFixedTime(endDate, hour, minute)

        scheduleReminder(context, goal.id, minus30, 1)
        scheduleReminder(context, goal.id, minus14, 2)
        scheduleReminder(context, goal.id, minus7, 3)
        scheduleReminder(context, goal.id, minus2, 4)
        scheduleReminder(context, goal.id, minus1, 5)
        scheduleReminder(context, goal.id, dayOf, 6)
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
