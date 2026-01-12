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
import java.time.LocalDate
import java.time.ZoneId

// DashboardBudgetNotificationReceiver.kt – odbiera alarmy i pokazuje powiadomienia o przekroczeniu budżetu.
class DashboardBudgetNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isNotificationsEnabled(context)) return
        val userId = Prefs.getUserId(context)
        if (userId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val monthlyBudgetDao = db.monthlyBudgetDao()
            val expenseDao = db.expenseDao()
            val now = LocalDate.now()
            val budgetEntity = monthlyBudgetDao.getBudgetForMonth(userId, now.year, now.monthValue)
            val budget = budgetEntity?.budget ?: return@launch
            val start = LocalDate.of(now.year, now.monthValue, 1)
            val end = now.withDayOfMonth(now.lengthOfMonth())
            val startMs = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMs = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() -1
            val expenses = expenseDao.getSumByCategoryForPeriod(userId, startMs, endMs)
            val total = expenses.sumOf { it.total ?: 0.0 }
            if (budget > 0 && total > budget) {
                val title = "⚠️ Przekroczyłeś budżet!"
                val text = "Budżet został przekroczony o %.2f zł.".format(total - budget)
                val id = 777777   // stały kod powiadomienia
                NotificationHelper.notifyBudget(context, id, title, text)
            }
        }
    }
}