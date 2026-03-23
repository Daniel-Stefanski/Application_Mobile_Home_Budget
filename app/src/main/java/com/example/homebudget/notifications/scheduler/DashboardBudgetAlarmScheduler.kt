package com.example.homebudget.notifications.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.homebudget.notifications.receiver.DashboardBudgetNotificationReceiver
import com.example.homebudget.utils.settings.Prefs
import java.util.Calendar

object DashboardBudgetAlarmScheduler {
    private fun pendingIntentFor(context: Context): PendingIntent {
        val intent = Intent(context, DashboardBudgetNotificationReceiver::class.java)
        val requestCode = 999999
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    fun scheduleDailyBudgetCheck(context: Context) {
        if (!Prefs.isNotificationsEnabled(context)) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context)
        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        try {
            val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canUseExactAlarm) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pi
                )
            } else if (canUseExactAlarm) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Fallback for phones without exact alarm permission.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pi
                )
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelScheduledBudgetCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(context)
        alarmManager.cancel(pi)
        pi.cancel()
    }
}
