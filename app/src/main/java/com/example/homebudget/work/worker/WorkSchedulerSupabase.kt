package com.example.homebudget.work.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.homebudget.data.sync.SupabaseSyncWorker
import java.util.concurrent.TimeUnit

object WorkSchedulerSupabase {

    fun scheduleSupabaseSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SupabaseSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("supabase_sync")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "supabase_sync",
                ExistingWorkPolicy.KEEP,
                request
            )
    }
}