package com.example.homebudget.data.dto

//CategorySum.kt – model danych pomocniczy do sumowania wydatków według kategorii.
data class CategorySum(
    val category: String?,
    val total: Double? = 0.0
)