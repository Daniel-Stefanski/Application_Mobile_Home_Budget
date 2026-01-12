package com.example.homebudget.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

//Settings.kt – model danych ustawień użytkownika.
@Entity(
    tableName = "settings",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Settings(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int, // powiązanie z użytkownikiem
    val categories: String, // zapisane jako JSON np. ["Jedzenie":"#ff5722","Transport"]
    val currency: String,
    val period: String, // np. "miesięczny"
    val savingsGoal: Double,
    val categoryColors: String = "{}", //zapisz jako JSON np. ["Jedzenie":"#ff5722"]
    val peopleList: String = "[]" //zpisujemy liste osób fikcyjnych
)