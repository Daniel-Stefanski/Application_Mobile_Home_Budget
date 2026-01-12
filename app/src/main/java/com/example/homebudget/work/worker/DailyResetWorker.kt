package com.example.homebudget.work.worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.homebudget.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class DailyResetWorker(context: Context, workerParams: WorkerParameters)
    : Worker(context, workerParams) {

    override fun doWork(): Result {

        val db = AppDatabase.Companion.getDatabase(applicationContext)
        val expenseDao = db.expenseDao()

        // Aktualna data
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH) + 1
        val currentYear = now.get(Calendar.YEAR)

        runBlocking {

            // Pobierz wszystkie rachunki cykliczne
            val recurringExpenses = expenseDao.getAllRecurringExpenses()

            recurringExpenses.forEach { expense ->

                //1) zabezpieczenie - czy reset był dzisiaj?
                val timeSinceLastReset = System.currentTimeMillis() - expense.lastReset
                val twentyHours = 20 * 60 * 60 * 1000L
                if (timeSinceLastReset < twentyHours) {
                    Log.d(
                        "DailyResetWorker",
                        "Pomijam reset – wykonano niedawno (ID=${expense.id})"
                    )
                    return@forEach
                }

                val lastPayment = Calendar.getInstance().apply {
                    timeInMillis = expense.date
                }

                val lastMonth = lastPayment.get(Calendar.MONTH) + 1
                val lastYear = lastPayment.get(Calendar.YEAR)

                // Ile miesięcy minęło?
                val monthsPassed =
                    (currentYear - lastYear) * 12 + (currentMonth - lastMonth)

                // Reset następuje jeśli minęło tyle miesięcy, ile wymagamy
                if (monthsPassed >= expense.repeatInterval) {

                    // RESET STATUSU
                    expenseDao.updateStatus(expense.id, "nieopłacony")

                    expenseDao.updateLastReset(expense.id, System.currentTimeMillis())

                    // PRZESUWAMY DATĘ NA NOWY CYKL
                    lastPayment.add(Calendar.MONTH, expense.repeatInterval)
                    expenseDao.updateExpenseFull(
                        expense.id,
                        expense.description ?: "",
                        expense.amount,
                        expense.note,
                        lastPayment.timeInMillis,
                        expense.repeatInterval
                    )
                    Log.d("DailyResetWorker", "Reset wykonany dla ID=${expense.id}")
                }
            }
        }

        return Result.success()
    }
}