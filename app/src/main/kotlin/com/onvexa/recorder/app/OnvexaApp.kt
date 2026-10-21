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
class OnvexaApp : Application() {

    @Inject lateinit var templates: TemplateBootstrap
    @Inject lateinit var demoSeeder: DemoSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            templates.ensure()
            demoSeeder.seedIfFirstRun()
        }
    }
}
