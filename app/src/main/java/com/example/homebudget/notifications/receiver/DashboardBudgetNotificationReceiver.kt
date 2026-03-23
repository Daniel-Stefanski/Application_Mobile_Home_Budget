package com.example.homebudget.notifications.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.notifications.NotificationHelper
import com.example.homebudget.notifications.scheduler.DashboardBudgetAlarmScheduler
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class DashboardBudgetNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isNotificationsEnabled(context)) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = Prefs.getUserId(context)
                if (userId == -1) return@launch

                val db = AppDatabase.getDatabase(context)
                val monthlyBudgetDao = db.monthlyBudgetDao()
                val expenseDao = db.expenseDao()
                val now = LocalDate.now()
                val budgetEntity = monthlyBudgetDao.getBudgetForMonth(userId, now.year, now.monthValue)
                val budget = budgetEntity?.budget ?: return@launch

                val start = LocalDate.of(now.year, now.monthValue, 1)
                val end = now.withDayOfMonth(now.lengthOfMonth())
                val startMs = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMs = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                val expenses = expenseDao.getSumByCategoryForPeriod(userId, startMs, endMs)
                val total = expenses.sumOf { it.total ?: 0.0 }

                if (budget > 0 && total > budget) {
                    NotificationHelper.createNotificationChannel(context)
                    NotificationHelper.notifyBudget(
                        context,
                        777777,
                        "Przekroczyles budzet!",
                        "Budzet zostal przekroczony o %.2f zl.".format(total - budget)
                    )
                }
            } finally {
                DashboardBudgetAlarmScheduler.scheduleDailyBudgetCheck(context)
                pendingResult.finish()
            }
        }
    }
}
