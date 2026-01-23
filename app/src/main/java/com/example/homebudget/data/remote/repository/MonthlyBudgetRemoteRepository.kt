package com.example.homebudget.data.remote.repository

import com.example.homebudget.data.entity.MonthlyBudget
import com.example.homebudget.data.remote.SupabaseClient
import com.example.homebudget.data.remote.dto.MonthlyBudgetRemoteDto
import com.example.homebudget.data.remote.dto.MonthlyBudgetRemoteWriteDto
import io.github.jan.supabase.postgrest.from

object MonthlyBudgetRemoteRepository {

    suspend fun upsertBudget(
        supabaseUid: String,
        budget: MonthlyBudget
    ) {
        val dto = MonthlyBudgetRemoteWriteDto(
            user_id = supabaseUid,
            year = budget.year,
            month = budget.month,
            budget = budget.budget,
            is_default = budget.isDefault
        )
        SupabaseClient.client
            .from("monthly_budgets")
            .upsert(dto)
    }

    suspend fun fetchAllBudgets(supabaseUid: String): List<MonthlyBudgetRemoteDto> {
        return SupabaseClient.client
            .from("monthly_budgets")
            .select {
                filter { eq("user_id", supabaseUid) }
            }
            .decodeList()
    }

    suspend fun deleteAllForUser(supabaseUid: String) {
        SupabaseClient.client
            .from("monthly_budgets")
            .delete {
                filter { eq("user_id", supabaseUid) }
            }
    }
}