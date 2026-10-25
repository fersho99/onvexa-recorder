package com.onvexa.recorder.wear

import android.app.Application
import com.onvexa.recorder.wear.demo.WatchDemoSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class WearApp : Application(), com.onvexa.recorder.core.audio.CaptureCompletionListener {

    @Inject lateinit var demoSeeder: WatchDemoSeeder
    @Inject lateinit var database: com.onvexa.recorder.core.database.AppDatabase
    @Inject lateinit var transfer: com.onvexa.recorder.core.audio.WearTransfer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            if (com.onvexa.recorder.core.audio.CaptureRuntime.state.value.phase == com.onvexa.recorder.core.audio.CapturePhase.Idle) database.recordingDao().markInterrupted(System.currentTimeMillis())
            transfer.schedule(periodic = true)
        }
    }
    override fun onRecordingSaved(id: String) { transfer.schedule() }
}
