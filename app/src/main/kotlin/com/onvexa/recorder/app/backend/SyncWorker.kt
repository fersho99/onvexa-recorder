package com.onvexa.recorder.app.backend

import android.content.Context
import androidx.work.*
import com.onvexa.recorder.app.OnvexaApp
import com.onvexa.recorder.app.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OnvexaApp
        return try {
            app.sync.sync()
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: BackendException) {
            if (e.status == 429 || e.status >= 500) Result.retry() else Result.failure()
        } catch (_: Exception) { Result.retry() }
    }
}
@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context, private val settings: SettingsRepository) {
    suspend fun schedule() {
        val wifiOnly = settings.settings.first().syncWifiOnly
        val constraints = Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork("onvexa-sync-periodic", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        manager.enqueueUniqueWork("onvexa-sync-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork("onvexa-sync-periodic")
        WorkManager.getInstance(context).cancelUniqueWork("onvexa-sync-now")
    }
}
