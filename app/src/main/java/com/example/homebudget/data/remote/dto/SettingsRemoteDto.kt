package com.example.homebudget.data.remote.dto

import com.example.homebudget.data.entity.Settings
import kotlinx.serialization.Serializable

@Serializable
data class SettingsRemoteDto(
    val user_id: String,
    val categories: String,
    val currency: String,
    val period: String,
    val savings_goal: Double,
    val category_colors: String,
    val people_list: String,
    val default_category: String,
    val default_payment_method: String
) {
    fun toLocal(userId: Int): Settings =
        Settings(
            userId = userId,
            categories = categories,
            currency = currency,
            period = period,
            savingsGoal = savings_goal,
            categoryColors = category_colors,
            peopleList = people_list,
            defaultCategory = default_category,
            defaultPaymentMethod = default_payment_method
        )

    companion object {
        fun fromLocal(settings: Settings, supabaseUid: String) =
            SettingsRemoteDto(
                user_id = supabaseUid,
                categories = settings.categories,
                currency = settings.currency,
                period = settings.period,
                savings_goal = settings.savingsGoal,
                category_colors = settings.categoryColors,
                people_list = settings.peopleList,
                default_category = settings.defaultCategory,
                default_payment_method = settings.defaultPaymentMethod
            )
    }
}