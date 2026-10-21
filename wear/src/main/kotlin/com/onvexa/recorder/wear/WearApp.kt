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
class WearApp : Application() {

    @Inject lateinit var demoSeeder: WatchDemoSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { demoSeeder.seedIfFirstRun() }
    }
}
