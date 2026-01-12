package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.homebudget.data.entity.Contribution

//ContributionDao.kt – interfejs DAO (zapytania do bazy kwoty kto ile się dołozył co celu oszczędnościowego).
@Dao
interface ContributionDao {
    @Insert
    suspend fun insert(contribution: Contribution)

    @Query("SELECT * FROM contributions WHERE goalId = :goalId ORDER BY timestamp DESC")
    suspend fun getContributionsForGoal(goalId: Int): List<Contribution>

    @Query("DELETE FROM contributions WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Int)

    @Query("SELECT SUM(amount) FROM contributions WHERE goalId = :goalId AND personName = :personName")
    suspend fun getTotalForPerson(goalId: Int, personName: String): Double?

}