package com.example.homebudget.data.remote.dto

import com.example.homebudget.data.entity.Expense
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseRemoteDto(
    val user_id: String,
    val category: String,
    val amount: Double,
    val description: String? = null,
    val note: String? = null,
    val payment_method: String,
    val date: Long,
    val timestamp: Long,
    val is_recurring: Boolean,
    val repeat_interval: Int,
    val person: String? = null,
    val status:  String = "nieopłacony",
    val last_reset: Long = 0
) {
    companion object {
        fun fromLocal(expense: Expense, supabaseUid: String): ExpenseRemoteDto =
            ExpenseRemoteDto(
                user_id = supabaseUid,
                category = expense.category,
                amount = expense.amount,
                description = expense.description,
                note = expense.note,
                payment_method = expense.paymentMethod,
                date = expense.date,
                timestamp = expense.timestamp,
                is_recurring = expense.isRecurring,
                repeat_interval = expense.repeatInterval,
                person = expense.person,
                status = expense.status,
                last_reset = expense.lastReset
            )
    }
}