package com.example.homebudget.data.sync

import android.content.Context
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

object SyncProcessor {
    suspend fun processPendingSync(context: Context): Boolean {
        val db = AppDatabase.getDatabase(context)
        val queue = db.pendingSyncDao().getAll()
        val supabaseUid = Prefs.getSupabaseUid(context) ?: return false

        queue.forEach { item ->
            try {
                when (item.entityType) {
                    SyncConstants.ENTITY_EXPENSE -> {
                        val expense = Json.decodeFromString(Expense.serializer(), item.payloadJson)

                        when (item.operation) {
                            SyncConstants.OP_INSERT -> {
                                val remoteId = ExpenseRemoteRepository.insertExpense(supabaseUid, expense)
                                item.localId?.let { db.expenseDao().updateRemoteId(it, remoteId) }
                            }

                            SyncConstants.OP_UPDATE -> {
                                val currentLocalExpense = item.localId?.let { db.expenseDao().getExpenseById(it) }
                                val rid = item.remoteId
                                    ?: currentLocalExpense?.remoteId
                                    ?: expense.remoteId
                                    ?: throw IllegalStateException("Brak remoteId dla Update expense")

                                val latestExpense = currentLocalExpense ?: expense
                                ExpenseRemoteRepository.updateExpense(supabaseUid, rid, latestExpense)
                            }

                            SyncConstants.OP_DELETE -> {
                                val currentLocalExpense = item.localId?.let { db.expenseDao().getExpenseById(it) }
                                val rid = item.remoteId
                                    ?: currentLocalExpense?.remoteId
                                    ?: expense.remoteId
                                    ?: throw IllegalStateException("Brak remoteId dla Delete expense")

                                ExpenseRemoteRepository.deleteExpense(rid)
                            }
                        }
                    }

                    SyncConstants.ENTITY_BUDGET -> {
                        val budget = Json.decodeFromString(MonthlyBudget.serializer(), item.payloadJson)
                        MonthlyBudgetRemoteRepository.upsertBudget(supabaseUid, budget)
                    }

                    SyncConstants.ENTITY_SAVINGS_GOAL -> {
                        val goal = Json.decodeFromString(SavingsGoal.serializer(), item.payloadJson)

                        when (item.operation) {
                            SyncConstants.OP_INSERT -> {
                                val remoteId = SavingsRemoteRepository.insertGoal(supabaseUid, goal)
                                item.localId?.let { db.savingsGoalDao().updateRemoteId(it, remoteId) }
                            }

                            SyncConstants.OP_UPDATE -> {
                                val currentLocalGoal = item.localId?.let { db.savingsGoalDao().getGoalById(it) }
                                val rid = item.remoteId
                                    ?: currentLocalGoal?.remoteId
                                    ?: goal.remoteId
                                    ?: throw IllegalStateException("Brak remoteId dla Update savings_goal")
                                val latestGoal = currentLocalGoal ?: goal
                                SavingsRemoteRepository.updateGoal(rid, latestGoal)
                            }

                            SyncConstants.OP_DELETE -> {
                                val currentLocalGoal = item.localId?.let { db.savingsGoalDao().getGoalById(it) }
                                val rid = item.remoteId
                                    ?: currentLocalGoal?.remoteId
                                    ?: goal.remoteId
                                    ?: throw IllegalStateException("Brak remoteId dla DELETE savings_goal")
                                SavingsRemoteRepository.deleteGoal(rid)
                            }
                        }
                    }

                    SyncConstants.ENTITY_CONTRIBUTION -> {
                        val contribution = Json.decodeFromString(Contribution.serializer(), item.payloadJson)
                        val goal = db.savingsGoalDao().getGoalById(contribution.goalId) ?: return@forEach
                        val remoteGoalId = goal.remoteId ?: return@forEach

                        val remoteId = SavingsRemoteRepository.insertContribution(
                            supabaseUid,
                            remoteGoalId,
                            contribution
                        )

                        item.localId?.let { db.contributionDao().updateRemoteId(it, remoteId) }
                    }

                    SyncConstants.ENTITY_SETTINGS -> {
                        val settings = Json.decodeFromString(Settings.serializer(), item.payloadJson)
                        SettingsRemoteRepository.upsertSettings(supabaseUid, settings)
                    }
                }
                db.pendingSyncDao().delete(item)
            } catch (e: Exception) {
                return false
            }
        }
        return true
    }
}