package com.example.homebudget.notifications.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler.DAY_MS
import com.example.homebudget.notifications.scheduler.DashboardBudgetAlarmScheduler
import com.example.homebudget.notifications.scheduler.SavingsGoalAlarmScheduler
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (!Prefs.isNotificationsEnabled(context)) return

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                DashboardBudgetAlarmScheduler.scheduleDailyBudgetCheck(context)

                val recurring = db.expenseDao().getAllRecurringExpenses()
                val now = System.currentTimeMillis()

                recurring.forEach { expense ->
                    if (expense.status == "opłacony") return@forEach

                    val cal = Calendar.getInstance()
                    cal.timeInMillis = expense.date
                    if (cal.timeInMillis < now) {
                        val diffMonths = ((now - cal.timeInMillis) / DAY_MS / 30).toInt()
                        val jumps = (diffMonths / expense.repeatInterval) * expense.repeatInterval
                        cal.add(Calendar.MONTH, jumps)
                        while (cal.timeInMillis < now) {
                            cal.add(Calendar.MONTH, expense.repeatInterval)
                        }
                    }
                    val nextCycleDate = cal.timeInMillis

                    val twoDaysBefore = nextCycleDate - 2 * DAY_MS
                    val oneDayBefore = nextCycleDate - DAY_MS

                    if (twoDaysBefore > now) {
                        BillsAlarmScheduler.scheduleReminder(context, expense.id, twoDaysBefore, 1)
                    }
                    if (oneDayBefore > now) {
                        BillsAlarmScheduler.scheduleReminder(context, expense.id, oneDayBefore, 2)
                    }
                    if (nextCycleDate > now) {
                        BillsAlarmScheduler.scheduleReminder(context, expense.id, nextCycleDate, 3)
                    }
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
