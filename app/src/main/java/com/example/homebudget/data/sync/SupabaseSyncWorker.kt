package com.example.homebudget.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.entity.Contribution
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.data.entity.MonthlyBudget
import com.example.homebudget.data.entity.SavingsGoal
import com.example.homebudget.data.entity.Settings
import com.example.homebudget.data.remote.repository.ExpenseRemoteRepository
import com.example.homebudget.data.remote.repository.MonthlyBudgetRemoteRepository
import com.example.homebudget.data.remote.repository.SavingsRemoteRepository
import com.example.homebudget.data.remote.repository.SettingsRemoteRepository
import com.example.homebudget.utils.settings.Prefs
import kotlinx.serialization.json.Json

class SupabaseSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {

        val db = AppDatabase.Companion.getDatabase(applicationContext)
        val queue = db.pendingSyncDao().getAll()
        val supabaseUid = Prefs.getSupabaseUid(applicationContext)
            ?: return Result.retry()

        queue.forEach { item ->
            try {
                when (item.entityType) {

                    SyncConstants.ENTITY_EXPENSE -> {
                        val expense = Json.Default.decodeFromString(Expense.serializer(), item.payloadJson)
                        when (item.operation) {
                            SyncConstants.OP_INSERT -> {
                                val remoteId = ExpenseRemoteRepository.insertExpense(supabaseUid, expense)
                                // Zapisz remoteId do lokalnego rekodru
                                if (item.localId != null) {
                                    db.expenseDao().updateRemoteId(item.localId, remoteId)
                                }
                            }

                            SyncConstants.OP_UPDATE -> {
                                val rid = item.remoteId
                                    ?: expense.remoteId
                                    ?: throw IllegalStateException("Brak remoteId dla UPDATE expense")
                                ExpenseRemoteRepository.updateExpense(
                                    supabaseUid = supabaseUid,
                                    remoteId = rid,
                                    expense = expense
                                )
                            }

                            SyncConstants.OP_DELETE -> {
                                val rid = item.remoteId
                                    ?: expense.remoteId
                                    ?: throw IllegalStateException("Brak remoteId dla DELETE expense")
                                ExpenseRemoteRepository.deleteExpense(rid)
                            }
                        }
                    }

                    SyncConstants.ENTITY_BUDGET -> {
                        val budget = Json.Default.decodeFromString(MonthlyBudget.serializer(), item.payloadJson)
                        MonthlyBudgetRemoteRepository.upsertBudget(supabaseUid, budget)
                    }

                    SyncConstants.ENTITY_SAVINGS_GOAL -> {
                        val goal = Json.decodeFromString(
                            SavingsGoal.serializer(), item.payloadJson
                        )
                        when (item.operation) {
                            SyncConstants.OP_INSERT -> {
                                val remoteId = SavingsRemoteRepository.insertGoal(supabaseUid, goal)
                                item.localId?.let {
                                    db.savingsGoalDao().updateRemoteId(it, remoteId)
                                }
                            }

                            SyncConstants.OP_UPDATE -> {
                                val rid = item.remoteId
                                    ?: goal.remoteId
                                    ?: throw IllegalStateException("Brak remoteId dla UPDATE savings_goal")
                                SavingsRemoteRepository.updateGoal(rid, goal)
                            }

                            SyncConstants.OP_DELETE -> {
                                val rid = item.remoteId
                                    ?: goal.remoteId
                                    ?: throw IllegalStateException("Brak remoteId dla DELETE savings_goal")
                                SavingsRemoteRepository.deleteGoal(rid)
                            }
                        }
                    }

                    SyncConstants.ENTITY_CONTRIBUTION -> {
                        val contribution = Json.decodeFromString(
                            Contribution.serializer(), item.payloadJson
                        )
                        val goal = db.savingsGoalDao().getGoalById(contribution.goalId)
                            ?: return@forEach // cel usunięty pomijamy
                        val remoteGoalId = goal.remoteId
                            ?: return@forEach // cel jeszcze nie zasynchronizowany
                        SavingsRemoteRepository.insertContribution(
                            supabaseUid,
                            remoteGoalId,
                            contribution
                        )
                    }

                    SyncConstants.ENTITY_SETTINGS -> {
                        val settings = Json.decodeFromString(
                            Settings.serializer(),
                            item.payloadJson
                        )

                        SettingsRemoteRepository.upsertSettings(
                            supabaseUid,
                            settings
                        )
                    }
                }

                // USUŃ Z KOLEJKI PO SUKCESIE
                db.pendingSyncDao().delete(item)

            } catch (e: Exception) {
                return Result.retry()
            }
        }

        return Result.success()
    }
}