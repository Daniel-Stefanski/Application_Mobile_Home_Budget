package com.example.homebudget.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    @ColumnInfo(name = "password")
    val passwordHash: String,
    val passwordSalt: String,
    val name: String,
    val createdAt: Long,
    val lastLogin: Long
)
