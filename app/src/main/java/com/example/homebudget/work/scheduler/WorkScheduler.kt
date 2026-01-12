package com.example.homebudget.work.scheduler

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.homebudget.work.worker.DailyResetWorker
import java.util.concurrent.TimeUnit

object WorkScheduler {

    fun scheduleDailyCheck(context: Context) {

        val workRequest = PeriodicWorkRequestBuilder<DailyResetWorker>(
            24, TimeUnit.HOURS,
            3, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_reset_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}