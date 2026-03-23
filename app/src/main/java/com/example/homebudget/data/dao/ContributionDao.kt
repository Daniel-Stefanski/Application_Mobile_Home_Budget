package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.homebudget.data.entity.Contribution

//ContributionDao.kt – interfejs DAO (zapytania do bazy kwoty kto ile się dołozył co celu oszczędnościowego).
@Dao
interface ContributionDao {
    @Insert
    suspend fun insert(contribution: Contribution): Long

    @Query("SELECT * FROM contributions WHERE goalId = :goalId ORDER BY timestamp DESC")
    suspend fun getContributionsForGoal(goalId: Int): List<Contribution>

    @Query("DELETE FROM contributions WHERE goalId = :goalId")
    suspend fun deleteByGoal(goalId: Int)

    @Query("SELECT SUM(amount) FROM contributions WHERE goalId = :goalId AND personName = :personName")
    suspend fun getTotalForPerson(goalId: Int, personName: String): Double?

    @Query("DELETE FROM contributions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Contribution>)

    @Query("SELECT * FROM contributions WHERE userId = :userId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(userId: Int, remoteId: Long): Contribution?

    @androidx.room.Update
    suspend fun update(contribution: Contribution)

    @Query("""
    DELETE FROM contributions 
<<<<<<< HEAD
    WHERE userId = :userId
    AND remoteId IS NOT NULL
    AND remoteId NOT IN (:remoteIds)
    """)
    suspend fun deleteNotInRemoteIds(userId: Int, remoteIds: List<Long>)

    @Query("UPDATE contributions SET remoteId = :remoteId WHERE id = :localId")
    suspend fun updateRemoteId(localId: Int, remoteId: Long)
=======
    WHERE goalId IN (
        SELECT id FROM savings_goals WHERE userId = :userId
    )
    AND remoteId IS NOT NULL
    AND remoteId NOT IN (:remoteIds)
""")
    suspend fun deleteNotInRemoteIds(userId: Int, remoteIds: List<Long>)
>>>>>>> abde858a44c9efea5cfceb369540cba69df30e1c
}