package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.homebudget.data.entity.PendingSync

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSync>

    @Insert
    suspend fun insert(item: PendingSync)

    @Delete
    suspend fun delete(item: PendingSync)
}