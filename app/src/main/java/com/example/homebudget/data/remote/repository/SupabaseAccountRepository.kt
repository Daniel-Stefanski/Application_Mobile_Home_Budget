package com.example.homebudget.data.remote.repository

import com.example.homebudget.data.remote.SupabaseClient
import io.github.jan.supabase.functions.functions

object SupabaseAccountRepository {

    suspend fun deleteAccount(supabaseUid: String) {
        SupabaseClient.client.functions.invoke(
            function = "delete-user",
            body = mapOf("user_id" to supabaseUid)
        )
    }
}