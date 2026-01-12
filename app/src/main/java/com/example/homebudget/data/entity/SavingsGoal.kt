package com.example.homebudget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

//SavingsGoal.kt – model danych pojedyńczego celu oszczędnościowego.
@Entity(tableName = "savings_goals")
data class SavingsGoal (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val title: String, //nazwa celu
    val targetAmount: Double, //kwota docelowa
    val savedAmount: Double = 0.0, //ile już mamy
    val endDate: Long? = null, //timestamp daty zakończenia (opcjonalne)
    val sharedWith: String? = null, //osoba, z którą cel jest wspólny
    val notificationCompletedSent: Boolean = false // jeśli powiadomienie poszło dla celu który ma 100%
)