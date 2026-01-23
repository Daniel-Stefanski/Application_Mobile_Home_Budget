package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.homebudget.data.dto.CategorySum
import com.example.homebudget.data.dto.PersonSum
import com.example.homebudget.data.entity.Expense

//ExpenseDao.kt – interfejs DAO (zapytania do bazy dla wydatków).
@Dao
interface ExpenseDao {
    // Wstawia wydatek i zwraca id (Long)
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<Expense>)

    // Lista wydatków dla usera (opcjonalnie - przydatne)
    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getAllExpensesForUser(userId: Int): List<Expense>

    // sumy wydatków wg kategorii dla danego usera
    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE userId = :userId GROUP BY category")
    suspend fun getSumByCategory(userId: Int): List<CategorySum>

    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE userId = :userId AND date BETWEEN :startDate AND :endDate GROUP BY category")
    suspend fun getSumByCategoryForPeriod(userId: Int, startDate: Long, endDate: Long): List<CategorySum>

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY date DESC")
    suspend fun getExpensesForUser(userId: Int): List<Expense>

    // Usuwa wszystkie wydatki danego usera
    @Query("DELETE FROM expenses WHERE userId = :userId")
    suspend fun deleteAll(userId: Int)

    // Checkbox rachunku/wydatku jak zaznaczymy
    @Query("SELECT * FROM expenses WHERE userId = :userId AND isRecurring = 1 ORDER BY date ASC")
    suspend fun getRecurringExpenses(userId: Int): List<Expense>

    @Query("UPDATE expenses SET isRecurring = 0 WHERE id = :expenseId")
    suspend fun unsetRecurring(expenseId: Int)

    @Query("UPDATE expenses SET description = :description, amount = :amount, note = :note, date = :date, repeatInterval = :interval WHERE id = :expenseId")
    suspend fun updateExpenseFull(expenseId: Int, description: String, amount: Double, note: String?, date: Long, interval: Int)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Int): Expense?

    // przydatne dla BootReceiver (jeśli chcesz obsłużyć wszystkie użytkownike)
    @Query("SELECT * FROM expenses WHERE isRecurring = 1")
    suspend fun getAllRecurringExpenses(): List<Expense>

    @Query("UPDATE expenses SET status = :status WHERE id = :expenseId")
    suspend fun updateStatus(expenseId: Int, status: String)

    @Query("UPDATE expenses SET lastReset = :time WHERE id = :id")
    suspend fun updateLastReset(id: Int, time: Long)

   @Query("SELECT person , SUM(amount) AS total FROM expenses WHERE userId = :userId AND date BETWEEN :startDate AND :endDate AND person IS NOT NULL GROUP BY person")
   suspend fun getSumByPersonForPeriod(userId: Int, startDate: Long, endDate: Long): List<PersonSum>

   @Query("UPDATE expenses SET date = :newDate WHERE id = :expenseId")
   suspend fun updateDate(expenseId: Int, newDate: Long)

    @Query("UPDATE expenses SET remoteId = :remoteId WHERE id = :localId")
    suspend fun updateRemoteId(localId: Int, remoteId: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)
}