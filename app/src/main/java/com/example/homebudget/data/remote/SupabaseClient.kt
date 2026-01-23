package com.example.homebudget.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.functions.Functions

object SupabaseClient {

    private const val SUPABASE_URL = "https://bmfjcjakzvkrlptbbsqd.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_M-WWKPNOSHeLUdVRUxtD1g_FpMRQnfL"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Auth)
        install(Functions)
    }
}