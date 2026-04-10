package com.example.homebudget.notifications.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.notifications.scheduler.DashboardBudgetAlarmScheduler
import com.example.homebudget.notifications.scheduler.SavingsGoalAlarmScheduler
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (!Prefs.isNotificationsEnabled(context)) return

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                DashboardBudgetAlarmScheduler.scheduleDailyBudgetCheck(context)

                val recurring = db.expenseDao().getAllRecurringExpenses()
                recurring.forEach { expense ->
                    if (expense.status == "op³acony") return@forEach

                    BillsAlarmScheduler.cancelAllReminders(context, expense.id)
                    BillsAlarmScheduler.scheduleAllRemindersForDate(context, expense.id, expense.date)
                }

                val goals = db.savingsGoalDao().getAllWithEndDate()
                goals.forEach { goal ->
                    if (goal.endDate != null && goal.savedAmount < goal.targetAmount) {
                        SavingsGoalAlarmScheduler.scheduleAllRemindersForGoal(context, goal)
                    }
                }
            }
        }
    }
}
