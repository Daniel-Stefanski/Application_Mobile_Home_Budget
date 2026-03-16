package com.example.homebudget.data.remote.repository

import com.example.homebudget.data.remote.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class DeleteUserRequest(
    val user_id: String
)

object SupabaseAccountRepository {

    suspend fun deleteAccount(supabaseUid: String) {
        val session = SupabaseClient.client.auth.currentSessionOrNull()
            ?: throw IllegalStateException("Brak aktywnej sesji użytkownika")

        val accessToken = session.accessToken
        val url = "https://bmfjcjakzvkrlptbbsqd.supabase.co/functions/v1/delete-user"

        val response = SupabaseClient.client.httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $accessToken")
            setBody(DeleteUserRequest(user_id = supabaseUid))
        }

        val responseText = response.bodyAsText()

        if (response.status.value !in 200..299) {
            throw IllegalStateException(
                "Delete account failed: ${response.status.value} - $responseText"
            )
        }
    }
}