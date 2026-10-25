package com.onvexa.recorder.app.transcription

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.onvexa.recorder.app.OnvexaApp
import com.onvexa.recorder.app.backend.BackendException
import kotlinx.coroutines.CancellationException

class TranscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        return try {
            val app = applicationContext as OnvexaApp
            if (app.models.isReady("spk")) {
                val manager = applicationContext.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("processing","Onvexa · Transcripcion",NotificationManager.IMPORTANCE_LOW))
                val notification = NotificationCompat.Builder(applicationContext,"processing").setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("Onvexa").setContentText("Transcribiendo en este dispositivo").setOngoing(true).build()
                val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                setForeground(ForegroundInfo(3101,notification,type))
            }
            app.processing.transcribe(id,runAttemptCount + 1)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
    }
}
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as OnvexaApp
        app.models.install(inputData.getString("language") ?: "es")
        app.processing.enqueueAll()
        Result.success()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { Result.failure() }
}
class AutomaticAiWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result { return try {
        (applicationContext as OnvexaApp).processing.automaticAi(inputData.getString("id") ?: return Result.failure())
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (e: BackendException) { if (e.status == 429 && runAttemptCount < 2) Result.retry() else Result.failure() }
    catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.failure() }
    }
}
