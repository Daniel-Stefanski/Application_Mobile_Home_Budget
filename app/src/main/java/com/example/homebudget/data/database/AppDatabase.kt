package com.example.homebudget.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.homebudget.data.dao.ContributionDao
import com.example.homebudget.data.dao.ExpenseDao
import com.example.homebudget.data.dao.MonthlyBudgetDao
import com.example.homebudget.data.dao.PendingSyncDao
import com.example.homebudget.data.dao.SavingsGoalDao
import com.example.homebudget.data.dao.SettingsDao
import com.example.homebudget.data.dao.UserDao
import com.example.homebudget.data.entity.Contribution
import com.example.homebudget.data.entity.Expense
import com.example.homebudget.data.entity.MonthlyBudget
import com.example.homebudget.data.entity.PendingSync
import com.example.homebudget.data.entity.SavingsGoal
import com.example.homebudget.data.entity.Settings
import com.example.homebudget.data.entity.User
import com.example.homebudget.utils.security.PasswordSecurity

@Database(
    entities = [
        User::class,
        Settings::class,
        Expense::class,
        SavingsGoal::class,
        MonthlyBudget::class,
        Contribution::class,
        PendingSync::class
    ],
    version = 51,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun settingsDao(): SettingsDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun monthlyBudgetDao(): MonthlyBudgetDao
    abstract fun contributionDao(): ContributionDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("PRAGMA table_info(pending_sync)")
                var hasEntityType = false
                while (cursor.moveToNext()) {
                    val columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (columnName == "entityType") {
                        hasEntityType = true
                        break
                    }
                }
                cursor.close()

                if (!hasEntityType) {
                    database.execSQL("ALTER TABLE pending_sync ADD COLUMN entityType STRING ")
                }
            }
        }

        private val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE users ADD COLUMN passwordSalt TEXT NOT NULL DEFAULT ''"
                )

                val cursor = database.query("SELECT id, password FROM users")
                while (cursor.moveToNext()) {
                    val userId = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                    val plainPassword =
                        cursor.getString(cursor.getColumnIndexOrThrow("password")) ?: ""
                    val credentials = PasswordSecurity.createCredentials(plainPassword)
                    val statement = database.compileStatement(
                        "UPDATE users SET password = ?, passwordSalt = ? WHERE id = ?"
                    )
                    statement.bindString(1, credentials.hash)
                    statement.bindString(2, credentials.salt)
                    statement.bindLong(3, userId.toLong())
                    statement.executeUpdateDelete()
                }
                cursor.close()
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "home_budget_db"
                )
                    .addMigrations(MIGRATION_49_50, MIGRATION_50_51)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
