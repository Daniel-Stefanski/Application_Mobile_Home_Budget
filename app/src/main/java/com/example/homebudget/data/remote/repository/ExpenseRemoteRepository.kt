package com.example.homebudget.data.remote.repository

import com.example.homebudget.data.entity.Expense
import com.example.homebudget.data.remote.SupabaseClient
import com.example.homebudget.data.remote.dto.ExpenseRemoteDto
import com.example.homebudget.data.remote.dto.ExpenseRemoteReadDto
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    suspend fun updateExpense(supabaseUid: String, remoteId: Long, expense: Expense): ExpenseRemoteReadDto {
        // select() po update wymusi informację zwrotną czy coś się zmieniło
        val payload = buildJsonObject {
            put("description", expense.description ?: "")
            put("amount", expense.amount)
            put("note",expense.note ?: "")
            put("date", expense.date)
            put("repeat_interval", expense.repeatInterval)
            put("status", expense.status)
            put("last_reset", expense.lastReset)
        }
        val updated = SupabaseClient.client
            .from("expenses")
            .update(payload) {
                filter {
                    eq("id", remoteId)
                    eq("user_id", supabaseUid)
                }
                select()
            }
            .decodeList<ExpenseRemoteReadDto>()
        if (updated.isEmpty()) {
            throw IllegalStateException("UPDATE w Supabase zmienił 0 wierszy (remoteId=$remoteId")
        }
        return updated.first()
    }

    suspend fun deleteExpense(remoteId: Long) {
        SupabaseClient.client
            .from("expenses")
            .delete {
                filter { eq("id", remoteId) }
            }
    }
}