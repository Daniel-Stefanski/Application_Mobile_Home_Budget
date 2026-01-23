package com.example.homebudget.data.remote.dto

import com.example.homebudget.data.entity.MonthlyBudget
import kotlinx.serialization.Serializable

@Serializable
data class MonthlyBudgetRemoteDto(
    val user_id: String,
    val year: Int,
    val month: Int,
    val budget: Double,
    val is_default: Boolean = false
) {
    fun toLocal(localUserId: Int): MonthlyBudget =
        MonthlyBudget(
            id = 0,
            userId = localUserId,
            year = year,
            month = month,
            budget = budget,
            isDefault = is_default
        )
}