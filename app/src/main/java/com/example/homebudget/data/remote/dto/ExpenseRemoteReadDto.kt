package com.example.homebudget.data.remote.dto

import com.example.homebudget.data.entity.Expense
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseRemoteReadDto(
    val id: Long? = null,
    val user_id: String,
    val category: String,
    val amount: Double,
    val description: String? = null,
    val note: String? = null,
    val payment_method: String,
    val date: Long,
    val timestamp: Long,
    val is_recurring: Boolean = false,
    val repeat_interval: Int = 1,
    val person: String? = null,
    val status: String = "nieopłacony",
    val last_reset: Long = 0
) {
    fun toLocal(localUserId: Int): Expense =
        Expense(
            id = 0, // local cache
            userId = localUserId,
            remoteId = id,
            category = category,
            amount = amount,
            description = description,
            note = note,
            paymentMethod = payment_method,
            date = date,
            timestamp = timestamp,
            isRecurring = is_recurring,
            repeatInterval = repeat_interval,
            person = person,
            status = status,
            lastReset = last_reset
        )
}