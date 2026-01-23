package com.example.homebudget.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SavingsGoalUpdateDto(
    val title: String,
    val target_amount: Double,
    val saved_amount: Double,
    val end_date: Long?,
    val shared_with: String?,
    val notification_completed_sent: Boolean
)