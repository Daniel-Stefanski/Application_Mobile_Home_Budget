package com.example.homebudget.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ContributionRemoteReadDto(
    val id: Long,
    val user_id: String,
    val goal_id: Long,
    val person_name: String,
    val amount: Double,
    val timestamp: Long
)