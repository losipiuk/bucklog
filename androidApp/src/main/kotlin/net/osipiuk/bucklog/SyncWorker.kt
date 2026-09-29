package net.osipiuk.bucklog

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import net.osipiuk.bucklog.google.AuthRequiredException
import net.osipiuk.bucklog.google.GoogleApiException
import net.osipiuk.bucklog.sync.SyncOutcome

/** Runs one sync (SPEC §6.2). Transient failures are retried with backoff; the error is shown in the app. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as BucklogApplication).graph
        val outcome = graph.sync.sync()
        if (outcome !is SyncOutcome.Failed) {
            // Weekly copy of the sheet, if enabled; a failure is recorded and retried next time.
            runCatching { graph.backups.backUpIfDue() }
            return Result.success()
        }
        val error = outcome.error
        val permanent = error is AuthRequiredException ||
            (error is GoogleApiException && error.httpStatus in 400..499 && error.httpStatus != 429)
        return if (permanent || runAttemptCount >= 5) Result.failure() else Result.retry()
    }
}

object SyncScheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Sync as soon as there's a network; if one is running, another runs right after it (never cancelled midway). */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync-periodic", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
