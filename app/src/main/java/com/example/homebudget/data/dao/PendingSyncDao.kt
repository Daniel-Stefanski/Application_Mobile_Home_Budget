package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.homebudget.data.entity.PendingSync

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSync>

    @Insert
    suspend fun insert(item: PendingSync)

    @Update
    suspend fun update(item: PendingSync)

    @Delete
    suspend fun delete(item: PendingSync)

    @Query("""
        SELECT * FROM pending_sync
        WHERE entityType = :entityType AND localId = :localId
        ORDER BY createdAt ASC LIMIT 1
    """)
    suspend fun findByEntityTypeAndLocalId(entityType: String, localId: Int): PendingSync?
}