package com.example.homebudget.data.remote.repository

import com.example.homebudget.data.entity.Expense
import com.example.homebudget.data.remote.SupabaseClient
import com.example.homebudget.data.remote.dto.ExpenseRemoteDto
import com.example.homebudget.data.remote.dto.ExpenseRemoteReadDto
import io.github.jan.supabase.postgrest.from

object ExpenseRemoteRepository {

    suspend fun insertExpense(
        supabaseUid: String,
        expense: Expense
    ): Long {
        val dto = ExpenseRemoteDto.fromLocal(expense, supabaseUid)

        val inserted = SupabaseClient.client
            .from("expenses")
            .insert(dto) { select()}
            .decodeSingle<ExpenseRemoteReadDto>()

        return inserted.id ?: throw IllegalStateException("Supabase nie zwrócił id po insert")
    }

    suspend fun fetchAllExpenses(supabaseUid: String): List<ExpenseRemoteReadDto> {
        return SupabaseClient.client
            .from("expenses")
            .select {
                filter {
                    eq("user_id", supabaseUid)
                }
            }
            .decodeList()
    }

    suspend fun deleteAllForUser(supabaseUid: String) {
        SupabaseClient.client
            .from("expenses")
            .delete {
                filter { eq("user_id", supabaseUid) }
            }
    }

    suspend fun updateExpense(remoteId: Long, expense: Expense) {
        SupabaseClient.client
            .from("expenses")
            .update(
                mapOf(
                    "description" to expense.description,
                    "amount" to expense.amount,
                    "note" to expense.note,
                    "date" to expense.date,
                    "repeat_interval" to expense.repeatInterval,
                    "status" to expense.status,
                    "last_reset" to expense.lastReset
                )
            ) {
                filter { eq("id", remoteId) }
            }
    }

    suspend fun deleteExpense(remoteId: Long) {
        SupabaseClient.client
            .from("expenses")
            .delete {
                filter { eq("id", remoteId) }
            }
    }
}