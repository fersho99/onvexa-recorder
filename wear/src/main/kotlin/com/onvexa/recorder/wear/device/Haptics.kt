package com.onvexa.recorder.wear.device

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.os.Vibrator
import android.os.Build
import com.onvexa.recorder.wear.settings.WatchSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

enum class HapticKind { Click, Strong, Success, Warning }

/** Vibración del reloj; respeta el ajuste de vibración. */
@Singleton
class Haptics @Inject constructor(
    @ApplicationContext context: Context,
    settings: WatchSettingsRepository,
) {
    @Suppress("DEPRECATION")
    private val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    @Volatile
    private var enabled = true

    init {
        settings.settings
            .onEach { enabled = it.haptics }
            .launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    fun perform(kind: HapticKind) {
        if (!enabled) return
        val effect = when (kind) {
            HapticKind.Click -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            HapticKind.Strong -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            HapticKind.Success -> VibrationEffect.createWaveform(longArrayOf(0, 40, 70, 40), -1)
            HapticKind.Warning -> VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1)
        }
        vibrator?.vibrate(effect)
    }
}
