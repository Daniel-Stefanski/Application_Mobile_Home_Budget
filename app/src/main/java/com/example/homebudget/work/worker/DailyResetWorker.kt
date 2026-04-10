package com.example.homebudget.work.worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.data.entity.PendingSync
import com.example.homebudget.data.remote.repository.ExpenseRemoteRepository
import com.example.homebudget.data.sync.PendingSyncHelper
import com.example.homebudget.data.sync.SyncConstants
import com.example.homebudget.notifications.scheduler.BillsAlarmScheduler
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar

class DailyResetWorker(context: Context, workerParams: WorkerParameters)
    : Worker(context, workerParams) {

    override fun doWork(): Result {

        val db = AppDatabase.Companion.getDatabase(applicationContext)
        val expenseDao = db.expenseDao()

        // Aktualna data
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH) + 1
        val currentYear = now.get(Calendar.YEAR)

        runBlocking {

            // Pobierz wszystkie rachunki cykliczne
            val recurringExpenses = expenseDao.getAllRecurringExpenses()

            recurringExpenses.forEach { expense ->

                //1) zabezpieczenie - czy reset był dzisiaj?
                val timeSinceLastReset = System.currentTimeMillis() - expense.lastReset
                val twentyHours = 20 * 60 * 60 * 1000L
                if (timeSinceLastReset < twentyHours) {
                    return@forEach
                }

                val lastPayment = Calendar.getInstance().apply {
                    timeInMillis = expense.date
                }

                val lastMonth = lastPayment.get(Calendar.MONTH) + 1
                val lastYear = lastPayment.get(Calendar.YEAR)

                // Ile miesięcy minęło?
                val monthsPassed =
                    (currentYear - lastYear) * 12 + (currentMonth - lastMonth)

                // Reset następuje jeśli minęło tyle miesięcy, ile wymagamy
                if (monthsPassed >= expense.repeatInterval) {
                    val newDate = lastPayment.apply {
                        add(Calendar.MONTH, expense.repeatInterval)
                    }.timeInMillis
                    val updatedExpense = expense.copy(
                        status = "nieopłacony",
                        lastReset = System.currentTimeMillis(),
                        date = newDate
                    )
                    // Room
                    expenseDao.updateExpense(updatedExpense)
                    BillsAlarmScheduler.cancelAllReminders(applicationContext, updatedExpense.id)
                    if (Prefs.isNotificationsEnabled(applicationContext)) {
                        BillsAlarmScheduler.scheduleAllRemindersForDate(
                            applicationContext,
                            updatedExpense.id,
                            updatedExpense.date
                        )
                    }
                    // Supabase
                    val supabaseUid = Prefs.getSupabaseUid(applicationContext)
                    if (!supabaseUid.isNullOrBlank() && updatedExpense.remoteId != null) {
                        try {
                            ExpenseRemoteRepository.updateExpense(
                                supabaseUid = supabaseUid,
                                remoteId = updatedExpense.remoteId!!,
                                expense = updatedExpense
                            )
                        } catch (e: Exception) {
                            PendingSyncHelper.enqueueOrMerge(
                                db.pendingSyncDao(),
                                PendingSync(
                                    entityType = SyncConstants.ENTITY_EXPENSE,
                                    operation = SyncConstants.OP_UPDATE,
                                    localId = updatedExpense.id,
                                    remoteId = updatedExpense.remoteId,
                                    payloadJson = Json.encodeToString(Expense.serializer(), updatedExpense)
                                )
                            )
                        }
                    } else {
                        PendingSyncHelper.enqueueOrMerge(
                            db.pendingSyncDao(),
                            PendingSync(
                                entityType = SyncConstants.ENTITY_EXPENSE,
                                operation = SyncConstants.OP_UPDATE,
                                localId = updatedExpense.id,
                                remoteId = updatedExpense.remoteId,
                                payloadJson = Json.encodeToString(Expense.serializer(), updatedExpense)
                            )
                        )
                    }
                    Log.d("DailyResetWorker", "CHECK id=${expense.id} remoteId=${expense.remoteId} monthsPassed=$monthsPassed repeat=${expense.repeatInterval} statusBefor=${expense.status}")
                }
            }
        }
        WorkSchedulerSupabase.scheduleSupabaseSync(applicationContext)
        return Result.success()
    }
}
