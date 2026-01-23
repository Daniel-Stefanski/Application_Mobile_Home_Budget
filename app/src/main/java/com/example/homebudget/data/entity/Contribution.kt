package com.example.homebudget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

//Contribution.kt – model danych do zapisuje pojedynczy wkład (wpłatę) od użytkownika lub osoby fikcyjnej
@Serializable
@Entity(tableName = "contributions")
data class Contribution (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: Long? = null,
    val userId: Int,       // powiązany użytkownik
    val goalId: Int,       // powiązany cel oszczędnościowy
    val personName: String, // kto wpłacił (np. "Tylko ja", "Ania", "Mama" itd.)
    val amount: Double,     // kwota wpłaty
    val timestamp: Long = System.currentTimeMillis() // data dodania
)