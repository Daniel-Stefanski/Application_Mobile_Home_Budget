package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.homebudget.data.entity.MonthlyBudget

// MonthlyBudgetDao.kt – obsługa zapytań dotyczących miesięcznych budżetów
@Dao
interface MonthlyBudgetDao {

    // Wstaw nowy budżet lub zastąp istniejący (dla danego miesiąca)
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertBudget(monthlyBudget: MonthlyBudget)

    // Aktualizacja istniejącego budżetu
    @Update
    suspend fun updateBudget(monthlyBudget: MonthlyBudget)

    // Pobranie budżetu użytkownika dla danego roku i miesiąca
    @Query("SELECT * FROM monthly_budgets WHERE userId = :userId AND year = :year AND month = :month LIMIT 1")
    suspend fun getBudgetForMonth(userId: Int, year: Int, month: Int): MonthlyBudget?

    // 🔹 DODANA METODA – potrzebna w DashboardActivity
    // Pobiera wszystkie budżety użytkownika (np. do znalezienia domyślnego)
    @Query("SELECT * FROM monthly_budgets WHERE userId = :userId")
    suspend fun getAllBudgetsForUser(userId: Int): List<MonthlyBudget>
}