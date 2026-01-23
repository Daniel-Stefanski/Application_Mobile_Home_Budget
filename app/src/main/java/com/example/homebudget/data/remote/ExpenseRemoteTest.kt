package com.example.homebudget.data.remote

import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun testFetchExpenses(): Result<Unit> {
    return try {
        withContext(Dispatchers.IO) {
            SupabaseClient.client
                .from("expenses")
                .select()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}