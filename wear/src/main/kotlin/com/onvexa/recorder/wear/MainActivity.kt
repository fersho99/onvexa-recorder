package com.onvexa.recorder.wear

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import com.onvexa.recorder.core.design.wear.OnvexaWearTheme
import com.onvexa.recorder.wear.recorder.RecorderController
import com.onvexa.recorder.wear.recorder.RecorderPhase
import com.onvexa.recorder.wear.settings.WatchSettingsRepository
import com.onvexa.recorder.wear.ui.Routes
import com.onvexa.recorder.wear.ui.WearNavHost
import com.onvexa.recorder.wear.ui.common.LocalAmbient
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var recorder: RecorderController
    @Inject lateinit var settings: WatchSettingsRepository

    private val ambient = mutableStateOf(false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            ambient.value = true
        }

        override fun onExitAmbient() {
            ambient.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))

        // Si el ajuste pide mantener la pantalla encendida, no se deja que el reloj entre en ambiente al grabar.
        lifecycleScope.launch {
            combine(recorder.state, settings.settings) { state, prefs ->
                state.phase != RecorderPhase.Idle && !prefs.dimWhileRecording
            }.collect { keepOn ->
                if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        val start = if (recorder.state.value.phase != RecorderPhase.Idle) Routes.RECORD else Routes.HOME
        setContent {
            CompositionLocalProvider(LocalAmbient provides ambient.value) {
                OnvexaWearTheme {
                    WearNavHost(startDestination = start)
                }
            }
        }
    }
}
