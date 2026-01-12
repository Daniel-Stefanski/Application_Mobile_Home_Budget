package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.homebudget.data.entity.Settings

//SettingsDao.kt – interfejs DAO (zapytania do bazy tabeli ustawień).
@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: Settings)

    @Update
    suspend fun update(settings: Settings)

    // Pobranie ustawień dla danego użytkownika
    @Query("SELECT * FROM settings WHERE userId = :userId LIMIT 1")
    suspend fun getSettingsForUser(userId: Int): Settings?

    // ✅ Zaktualizowana wersja zapytania updateSettings – dodano defaultBudget
    @Query("UPDATE settings SET  " +
            "categories = :categories, " +
            "currency = :currency, " +
            "period = :period, " +
            "savingsGoal = :savingsGoal, " +
            "categoryColors = :categoryColors, " +
            "peopleList = :peopleList " +
            "WHERE userId = :userId")
    suspend fun updateSettings(
        userId: Int,
        categories: String,
        currency: String,
        period: String,
        savingsGoal: Double,
        categoryColors: String,
        peopleList: String
    )
}