package com.example.homebudget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

//MonthlyBudget.kt – model danych miesięcznych kwot budżetu.
@Serializable
@Entity(tableName = "monthly_budgets")
data class MonthlyBudget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val year: Int,
    val month: Int,
    val budget: Double,        // faktyczny budżet na ten miesiąc
    val isDefault: Boolean = false  // jeśli true, oznacza powtarzający się budżet
)