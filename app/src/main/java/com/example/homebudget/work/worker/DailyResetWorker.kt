package com.example.homebudget.work.worker

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
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

class DailyResetWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val expenseDao = db.expenseDao()
        val supabaseUid = Prefs.getSupabaseUid(applicationContext)
        val notificationsEnabled = Prefs.isNotificationsEnabled(applicationContext)

        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH) + 1
        val currentYear = now.get(Calendar.YEAR)

        runBlocking {
            val recurringExpenses = expenseDao.getAllRecurringExpenses()

            recurringExpenses.forEach { expense ->
                val resetTimestamp = System.currentTimeMillis()
                val timeSinceLastReset = resetTimestamp - expense.lastReset
                val twentyHoursMs = 20 * 60 * 60 * 1000L

                if (timeSinceLastReset < twentyHoursMs) {
                    return@forEach
                }

                val currentBillDate = Calendar.getInstance().apply {
                    timeInMillis = expense.date
                }

                val lastMonth = currentBillDate.get(Calendar.MONTH) + 1
                val lastYear = currentBillDate.get(Calendar.YEAR)
                val monthsPassed = (currentYear - lastYear) * 12 + (currentMonth - lastMonth)

                if (monthsPassed < expense.repeatInterval) {
                    return@forEach
                }

                val nextDate = Calendar.getInstance().apply {
                    timeInMillis = expense.date
                    add(Calendar.MONTH, expense.repeatInterval)
                }.timeInMillis

                // Archive the finished cycle so it stays in history/statistics.
                val archivedExpense = expense.copy(
                    isRecurring = false,
                    lastReset = resetTimestamp
                )

                // Create a fresh recurring bill for the next cycle.
                val nextRecurringTemplate = expense.copy(
                    id = 0,
                    remoteId = null,
                    date = nextDate,
                    timestamp = resetTimestamp,
                    status = "nieopłacony",
                    lastReset = resetTimestamp
                )

                var nextRecurringExpense = nextRecurringTemplate

                db.withTransaction {
                    expenseDao.updateExpense(archivedExpense)
                    val newLocalId = expenseDao.insertExpense(nextRecurringTemplate).toInt()
                    nextRecurringExpense = nextRecurringTemplate.copy(id = newLocalId)
                }

                BillsAlarmScheduler.cancelAllReminders(applicationContext, archivedExpense.id)
                if (notificationsEnabled) {
                    BillsAlarmScheduler.scheduleAllRemindersForDate(
                        applicationContext,
                        nextRecurringExpense.id,
                        nextRecurringExpense.date
                    )
                }

                syncArchivedExpense(db, supabaseUid, archivedExpense)
                syncNextRecurringExpense(db, supabaseUid, nextRecurringExpense)

                Log.d(
                    "DailyResetWorker",
                    "Archived bill id=${archivedExpense.id}, created next bill id=${nextRecurringExpense.id}, nextDate=${nextRecurringExpense.date}, previousStatus=${archivedExpense.status}"
                )
            }
        }

        WorkSchedulerSupabase.scheduleSupabaseSync(applicationContext)
        return Result.success()
    }

    private suspend fun syncArchivedExpense(
        db: AppDatabase,
        supabaseUid: String?,
        archivedExpense: Expense
    ) {
        if (!supabaseUid.isNullOrBlank() && archivedExpense.remoteId != null) {
            try {
                ExpenseRemoteRepository.updateExpense(
                    supabaseUid = supabaseUid,
                    remoteId = archivedExpense.remoteId,
                    expense = archivedExpense
                )
                return
            } catch (_: Exception) {
                // Fall back to the queue below.
            }
        }

        val existingPending = db.pendingSyncDao().findByEntityTypeAndLocalId(
            SyncConstants.ENTITY_EXPENSE,
            archivedExpense.id
        )
        val operation = when {
            archivedExpense.remoteId != null -> SyncConstants.OP_UPDATE
            existingPending != null -> SyncConstants.OP_UPDATE
            else -> SyncConstants.OP_INSERT
        }

        PendingSyncHelper.enqueueOrMerge(
            db.pendingSyncDao(),
            PendingSync(
                entityType = SyncConstants.ENTITY_EXPENSE,
                operation = operation,
                localId = archivedExpense.id,
                remoteId = archivedExpense.remoteId,
                payloadJson = Json.encodeToString(Expense.serializer(), archivedExpense)
            )
        )
    }

    private suspend fun syncNextRecurringExpense(
        db: AppDatabase,
        supabaseUid: String?,
        nextRecurringExpense: Expense
    ) {
        if (!supabaseUid.isNullOrBlank()) {
            try {
                val remoteId = ExpenseRemoteRepository.insertExpense(
                    supabaseUid = supabaseUid,
                    expense = nextRecurringExpense
                )
                db.expenseDao().updateRemoteId(nextRecurringExpense.id, remoteId)
                return
            } catch (_: Exception) {
                // Fall back to the queue below.
            }
        }

        PendingSyncHelper.enqueueOrMerge(
            db.pendingSyncDao(),
            PendingSync(
                entityType = SyncConstants.ENTITY_EXPENSE,
                operation = SyncConstants.OP_INSERT,
                localId = nextRecurringExpense.id,
                remoteId = null,
                payloadJson = Json.encodeToString(Expense.serializer(), nextRecurringExpense)
            )
        )
    }
}
