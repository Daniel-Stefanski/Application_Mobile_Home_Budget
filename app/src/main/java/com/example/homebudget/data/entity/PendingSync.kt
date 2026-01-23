package com.example.homebudget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_sync")
data class PendingSync(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val entityType: String, // "expense", "bill", "savings_goal", "contribution", "settings", "budget"
    val operation: String, // "INSERT", "UPDATE", "DELETE"
    val localId: Int?, // id w Room
    val remoteId: Long?, // id w Supabase (jeśli istnieje)
    val payloadJson: String, // Snapshot danych
    val createdAt: Long = System.currentTimeMillis()
)