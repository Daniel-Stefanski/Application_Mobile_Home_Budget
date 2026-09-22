package com.example.homebudget.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.homebudget.data.entity.User

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users")
    suspend fun getAllusers(): List<User>

    @Query("UPDATE users SET password = :passwordHash, passwordSalt = :passwordSalt WHERE username = :email")
    suspend fun updatePasswordCredentials(email: String, passwordHash: String, passwordSalt: String)

    @Query("UPDATE users SET lastLogin = :lastLogin WHERE id = :userId")
    suspend fun updateLastLogin(userId: Int, lastLogin: Long)

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): User?

    @Query("UPDATE users SET name = :newName WHERE id = :userId")
    suspend fun updateUserName(userId: Int, newName: String)

    @Query("UPDATE users SET username = :newEmail WHERE id = :userId")
    suspend fun updateUserEmail(userId: Int, newEmail: String)

    @Query("UPDATE users SET password = :passwordHash, passwordSalt = :passwordSalt WHERE id = :userId")
    suspend fun updateUserPasswordCredentials(userId: Int, passwordHash: String, passwordSalt: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Int)
}
