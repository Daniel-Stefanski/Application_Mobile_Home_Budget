package com.example.homebudget.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import kotlinx.serialization.Serializable

//Expense.kt – model danych pojedynczego wydatku.
@Serializable
@Entity(tableName = "expenses",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["date"]),
        Index(value = ["isRecurring"]),
        Index(value = ["status"]),
        Index(value = ["category"]),
        Index(value = ["repeatInterval"])
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val remoteId: Long? = null,
    val category: String,
    val amount: Double,
    val description: String?, //ktrótki opis wydatku
    val note: String?, //dodatkowa notatka
    val paymentMethod: String, //np.Karta, Gotówka, Blik
    val date: Long, //data wydatku (timestamp)
    val timestamp: Long, //zapisujemy kiedy dodano rekord jako timestamp (System.currentTimeMillis())
    val isRecurring: Boolean = false, //czy wydatek powtarzalny
    val repeatInterval: Int = 1, // co ile miesięcy (1, 2, 3, 6, 12)
    val person: String? = null, //czyj wydatek
    val status: String = "nieopłacony", // lub opłacony
    val lastReset: Long = 0 //timestamp ostatniego resetu
)