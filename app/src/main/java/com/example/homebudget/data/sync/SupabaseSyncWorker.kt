package com.example.homebudget.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SupabaseSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return if (SyncProcessor.processPendingSync(applicationContext)) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}