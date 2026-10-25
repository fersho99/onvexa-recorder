package com.onvexa.recorder.app

import android.app.Application
import com.onvexa.recorder.app.data.TemplateBootstrap
import com.onvexa.recorder.app.demo.DemoSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class OnvexaApp : Application(), com.onvexa.recorder.core.audio.CaptureCompletionListener, com.onvexa.recorder.core.audio.WearChangeListener {

    @Inject lateinit var templates: TemplateBootstrap
    @Inject lateinit var demoSeeder: DemoSeeder
    @Inject lateinit var sync: com.onvexa.recorder.app.backend.SyncRepository
    @Inject lateinit var syncScheduler: com.onvexa.recorder.app.backend.SyncScheduler
    @Inject lateinit var auth: com.onvexa.recorder.app.backend.AuthRepository
    @Inject lateinit var models: com.onvexa.recorder.app.transcription.ModelRepository
    @Inject lateinit var processing: com.onvexa.recorder.app.transcription.ProcessingRepository
    @Inject lateinit var database: com.onvexa.recorder.core.database.AppDatabase
    @Inject lateinit var transfer: com.onvexa.recorder.core.audio.WearTransfer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            if (com.onvexa.recorder.core.audio.CaptureRuntime.state.value.phase == com.onvexa.recorder.core.audio.CapturePhase.Idle) database.recordingDao().markInterrupted(System.currentTimeMillis())
            templates.ensure()
            // Los ejemplos solo se cargan mediante la accion explicita de Ajustes.
            if (auth.account.value != null && auth.syncEnabled) syncScheduler.schedule()
            processing.enqueueAll()
            transfer.schedule(periodic = true)
        }
    }
    override fun onRecordingSaved(id: String) { appScope.launch { processing.enqueue(id); transfer.schedule(); if (auth.syncEnabled && auth.account.value != null) syncScheduler.schedule() } }
    override fun onWearChanged() { appScope.launch { if (auth.syncEnabled && auth.account.value != null) syncScheduler.schedule() } }
}
