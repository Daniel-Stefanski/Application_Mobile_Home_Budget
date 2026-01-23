package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.homebudget.data.entity.SavingsGoal

//SavingsGoalDao.kt – interfejs DAO (zapytania do bazy dla celów oszczędnościowych).
@Dao
interface SavingsGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoal): Long

    @Query("SELECT * FROM savings_goals WHERE userId = :userId")
    suspend fun getGoalsForUser(userId: Int): List<SavingsGoal>

    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)

    // Pobieranie jednego celu po id (dla powiadomień)
    @Query("SELECT * FROM savings_goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: Int): SavingsGoal?

    // Wszystkie cele z terminem (dla BootReceiver)
    @Query("SELECT * FROM savings_goals WHERE endDate IS NOT NULL")
    suspend fun getAllWithEndDate(): List<SavingsGoal>

    @Query("DELETE FROM savings_goals WHERE userId = :userId")
    suspend fun deleteAll(userId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<SavingsGoal>)

    @Query("UPDATE savings_goals SET remoteId = :remoteId WHERE id = :localId")
    suspend fun updateRemoteId(localId: Int, remoteId: Long)
}