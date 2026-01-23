package com.example.homebudget.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MonthlyBudgetRemoteWriteDto(
    val user_id: String,
    val year: Int,
    val month: Int,
    val budget: Double,
    val is_default: Boolean
)