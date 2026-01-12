package com.example.homebudget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

//User.kt – model danych użytkownika (zawiera m.in. imię/nick, e-mail, hasło).
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,   // email jako login
    val password: String,
    val name: String,        // imię użytkownika
    val createdAt: Long, // data utworzenia konta
    val lastLogin: Long // data ostaniego logowania
)