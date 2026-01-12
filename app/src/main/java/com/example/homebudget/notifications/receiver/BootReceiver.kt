package com.example.homebudget.notifications.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler.DAY_MS
import com.example.homebudget.notifications.scheduler.SavingsGoalAlarmScheduler
import com.example.homebudget.utils.settings.Prefs
import java.util.Calendar

//BootReceiver.kt – przywraca zaplanowane przypomnienia po restarcie telefonu.
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (!Prefs.isNotificationsEnabled(context)) return

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)

                // Przywracanie rachunków
                val recurring = db.expenseDao().getAllRecurringExpenses()
                val now = System.currentTimeMillis()

                recurring.forEach { expense ->
                    // jeśli już opłacony -> pomijamy
                    if (expense.status == "opłacony") return@forEach

                    val cal = Calendar.getInstance()
                    cal.timeInMillis = expense.date
                    // przesuwamy datę na pierwszą datę >= teraz (kolejny cykl)
                    if (cal.timeInMillis < now) {
                        val diffMonths =
                            ((now - cal.timeInMillis) / DAY_MS / 30).toInt() // ≈ liczba miesięcy
                        val jumps = (diffMonths / expense.repeatInterval) * expense.repeatInterval
                        cal.add(Calendar.MONTH, jumps)
                        while (cal.timeInMillis < now) {
                            cal.add(Calendar.MONTH, expense.repeatInterval)
                        }
                    }
                    val nextCycleDate = cal.timeInMillis

                    // zaplanuj 3 przypomnienia dla nextCycleDate, ale tylko jeśli terminy > teraz
                    val twoDaysBefore = nextCycleDate - 2 * DAY_MS
                    val oneDayBefore = nextCycleDate - 1 * DAY_MS

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

                // Przywaracanie powiadomień dla celów
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