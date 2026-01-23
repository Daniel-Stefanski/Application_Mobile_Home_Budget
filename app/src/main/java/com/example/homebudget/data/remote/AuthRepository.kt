package com.example.homebudget.data.remote

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo

object AuthRepository {

    suspend fun signUp(email: String, password: String): Result<UserInfo> {
        return try {
            // wykonuje rejestrację w Supabase Auth
            SupabaseClient.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            // po rejestracji pobieramy zalogowanego usera
            val user = SupabaseClient.client.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Brak użytkownika po rejestracji (currentUserOrNull = null)"))

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<UserInfo> {
        return try {
            // logowanie do Supabase Auth
            SupabaseClient.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val user = SupabaseClient.client.auth.currentUserOrNull()
                ?: return Result.failure(IllegalStateException("Brak użytkownika po logowaniu (currentUserOrNull = null)"))

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUser(): UserInfo? =
        SupabaseClient.client.auth.currentUserOrNull()
}