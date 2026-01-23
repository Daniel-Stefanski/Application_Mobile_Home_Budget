package com.example.homebudget.data.remote.repository

import com.example.homebudget.data.entity.Settings
import com.example.homebudget.data.remote.SupabaseClient
import com.example.homebudget.data.remote.dto.SettingsRemoteDto
import io.github.jan.supabase.postgrest.from

object SettingsRemoteRepository {

    suspend fun upsertSettings(
        supabaseUid: String,
        settings: Settings
    ) {
        val dto = SettingsRemoteDto.fromLocal(settings, supabaseUid)

        SupabaseClient.client
            .from("settings")
            .upsert(dto, onConflict = "user_id")
    }

    suspend fun fetchSettings(
        supabaseUid: String
    ): SettingsRemoteDto? {
        return SupabaseClient.client
            .from("settings")
            .select {
                filter { eq("user_id", supabaseUid) }
            }
            .decodeSingleOrNull()
    }

    suspend fun resetSettings(supabaseUid: String, defaultSettings: Settings) {
        val dto = SettingsRemoteDto.fromLocal(defaultSettings, supabaseUid)

        SupabaseClient.client
            .from("settings")
            .upsert(dto, onConflict = "user_id")

    }

    suspend fun deleteSettings(supabaseUid: String) {
        SupabaseClient.client
            .from("settings")
            .delete {
                filter { eq("user_id", supabaseUid) }
            }
    }
}