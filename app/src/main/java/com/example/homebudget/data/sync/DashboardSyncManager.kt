package com.example.homebudget.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.example.homebudget.data.database.AppDatabase
import com.example.homebudget.data.remote.dto.toLocal
import com.example.homebudget.data.remote.repository.ExpenseRemoteRepository
import com.example.homebudget.data.remote.repository.MonthlyBudgetRemoteRepository
import com.example.homebudget.data.remote.repository.SavingsRemoteRepository
import com.example.homebudget.data.remote.repository.SettingsRemoteRepository
import com.example.homebudget.utils.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DashboardSyncManager {

    suspend fun sync(context: Context): Boolean = withContext(Dispatchers.IO) {
        val localUserId = Prefs.getUserId(context)
        val supabaseUid = Prefs.getSupabaseUid(context)

        if (localUserId == -1 || supabaseUid.isNullOrBlank()) return@withContext false

        try {
            val db = AppDatabase.getDatabase(context)
            val expenseDao = db.expenseDao()
            val budgetDao = db.monthlyBudgetDao()
            val savingsGoalDao = db.savingsGoalDao()
            val contributionDao = db.contributionDao()
            val settingsDao = db.settingsDao()

            val remoteExpenses = ExpenseRemoteRepository.fetchAllExpenses(supabaseUid)
            val remoteBudgets = MonthlyBudgetRemoteRepository.fetchAllBudgets(supabaseUid)
            val remoteGoals = SavingsRemoteRepository.fetchGoals(supabaseUid)
            val remoteContributions = SavingsRemoteRepository.fetchContributions(supabaseUid)
            val remoteSettings = SettingsRemoteRepository.fetchSettings(supabaseUid)

            db.withTransaction {
                remoteExpenses.forEach { remote ->
                    val remoteId = remote.id ?: return@forEach
                    val localExpense = remote.toLocal(localUserId)
                    val existing = expenseDao.getByRemoteId(localUserId, remoteId)

                    if (existing == null) {
                        expenseDao.insertExpense(localExpense)
                    } else {
                        expenseDao.updateExpense(localExpense.copy(id = existing.id))
                    }
                }

                val remoteExpenseIds = remoteExpenses.mapNotNull { it.id }
                if (remoteExpenseIds.isEmpty()) {
                    expenseDao.deleteAll(localUserId)
                } else {
                    expenseDao.deleteNotInRemoteIds(localUserId, remoteExpenseIds)
                }

                remoteBudgets.forEach { remote ->
                    val localBudget = remote.toLocal(localUserId)
                    val existing = budgetDao.getByYearMonth(localUserId, remote.year, remote.month)

                    if (existing == null) {
                        budgetDao.insertBudget(localBudget)
                    } else {
                        budgetDao.updateBudget(localBudget.copy(id = existing.id))
                    }
                }

                if (remoteSettings != null) {
                    val localSettings = remoteSettings.toLocal(localUserId)
                    val existingSettings = settingsDao.getSettingsForUser(localUserId)

                    if (existingSettings == null) {
                        settingsDao.insertSettings(localSettings)
                    } else {
                        settingsDao.update(localSettings.copy(userId = existingSettings.userId))
                    }
                }

                remoteGoals.forEach { remote ->
                    val localGoal = remote.toLocal(localUserId)
                    val existing = savingsGoalDao.getByRemoteId(localUserId, remote.id)

                    if (existing == null) {
                        savingsGoalDao.insert(localGoal)
                    } else {
                        savingsGoalDao.update(localGoal.copy(id = existing.id))
                    }
                }

                val remoteGoalIds = remoteGoals.map { it.id }
                if (remoteGoals.isEmpty()) {
                    savingsGoalDao.deleteAll(localUserId)
                } else {
                    savingsGoalDao.deleteNotInRemoteIds(localUserId, remoteGoalIds)
                }

                val savedGoals = savingsGoalDao.getGoalsForUser(localUserId)
                val goalIdMap = savedGoals
                    .filter { it.remoteId != null }
                    .associate { it.remoteId!! to it.id }

                val localContributions = remoteContributions.mapNotNull { remote ->
                    val localGoalId = goalIdMap[remote.goal_id]
                    localGoalId?.let { remote.toLocal(localUserId, it) }
                }

                localContributions.forEach { contribution ->
                    val remoteId = contribution.remoteId ?: return@forEach
                    val existing = contributionDao.getByRemoteId(localUserId, remoteId)

                    if (existing == null) {
                        contributionDao.insert(contribution)
                    } else {
                        contributionDao.update(contribution.copy(id = existing.id))
                    }
                }

                val remoteContributionIds = remoteContributions.map { it.id }
                if (remoteContributions.isEmpty()) {
                    contributionDao.deleteAllForUser(localUserId)
                } else {
                    contributionDao.deleteNotInRemoteIds(localUserId, remoteContributionIds)
                }
            }

            true
        } catch (_: Exception) {
            false
        }
    }
}
