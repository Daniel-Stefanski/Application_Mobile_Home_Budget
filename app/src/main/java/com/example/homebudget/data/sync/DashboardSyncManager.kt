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

    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        val localUserId = Prefs.getUserId(context)
        val supabaseUid = Prefs.getSupabaseUid(context)

        if (localUserId == -1 || supabaseUid.isNullOrBlank()) return@withContext

        val db = AppDatabase.getDatabase(context)
        val expenseDao = db.expenseDao()
        val budgetDao = db.monthlyBudgetDao()
        val savingsGoalDao = db.savingsGoalDao()
        val contributionDao = db.contributionDao()

        val remoteExpenses = ExpenseRemoteRepository.fetchAllExpenses(supabaseUid)
        val remoteBudgets = MonthlyBudgetRemoteRepository.fetchAllBudgets(supabaseUid)

        val localExpenses = remoteExpenses.map { it.toLocal(localUserId) }
        val localBudgets = remoteBudgets.map { it.toLocal(localUserId) }

        db.withTransaction {
            expenseDao.deleteAll(localUserId)
            expenseDao.insertAll(localExpenses)

            budgetDao.deleteAll(localUserId)
            budgetDao.insertAll(localBudgets)
        }

        val remoteSettings = SettingsRemoteRepository.fetchSettings(supabaseUid)
        if (remoteSettings != null) {
            val localSettings = remoteSettings.toLocal(localUserId)
            db.settingsDao().insertSettings(localSettings)
        }

        val remoteGoals = SavingsRemoteRepository.fetchGoals(supabaseUid)
        val remoteContributions= SavingsRemoteRepository.fetchContributions(supabaseUid)

        val localGoals = remoteGoals.map { it.toLocal(localUserId) }

        db.withTransaction {
            savingsGoalDao.deleteAll(localUserId)
            savingsGoalDao.insertAll(localGoals)
        }
        val savedGoals = savingsGoalDao.getGoalsForUser(localUserId)
        val goalIdMap = savedGoals
            .filter { it.remoteId != null }
            .associate { it.remoteId!! to it.id }
        val localContributions = remoteContributions.mapNotNull { remote ->
            val localGoalId = goalIdMap[remote.goal_id]
            localGoalId?.let {
                remote.toLocal(localUserId, it)
            }
        }

        db.withTransaction {
            contributionDao.deleteAllForUser(localUserId)
            contributionDao.insertAll(localContributions)
        }
    }
}