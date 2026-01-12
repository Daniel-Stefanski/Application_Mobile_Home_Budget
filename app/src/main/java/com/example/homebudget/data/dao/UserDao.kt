package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.homebudget.data.entity.User

//UserDao.kt – interfejs DAO dla użytkowników (logowanie, rejestracja, itp.).
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long //zwraca ID nowego użytkownika

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users")
    suspend fun getAllusers(): List<User>

    @Query("UPDATE users SET password = :newPassword WHERE username = :email")
    suspend fun updatePassword(email: String, newPassword: String)

    @Query("UPDATE users SET lastLogin = :lastLogin WHERE id = :userId")
    suspend fun updateLastLogin(userId: Int, lastLogin: Long)

    // Do edycji danych
    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): User?

    @Query("UPDATE users SET name = :newName WHERE id = :userId")
    suspend fun updateUserName(userId: Int, newName: String)

    @Query("UPDATE users SET username = :newEmail WHERE id = :userId")
    suspend fun updateUserEmail(userId: Int, newEmail: String)

    @Query("UPDATE users SET password = :newPassword WHERE id = :userId")
    suspend fun updateUserPassword(userId: Int, newPassword: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Int)
}