package com.blackeye.kribo.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blackeye.kribo.KriboApplication
import java.util.concurrent.TimeUnit

/** Lazily touches WorkManager only when the user enables automatic sync. */
class DriveSyncScheduler(
    context: Context,
    private val prefs: DrivePrefs,
) {
    private val appContext = context.applicationContext

    fun update() {
        if (!prefs.autoSync) {
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()
        val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        const val WORK_NAME = "kribo-drive-auto-sync"
    }
}

class DriveSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? KriboApplication ?: return Result.success()
        return when (app.container.driveSyncManager.sync()) {
            is DriveSyncResult.Success -> Result.success()
            DriveSyncResult.AuthRequired -> Result.success()
            is DriveSyncResult.Error -> Result.retry()
        }
    }
}
