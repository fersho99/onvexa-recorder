package com.onvexa.recorder.wear.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.watchStore by preferencesDataStore(name = "watch_settings")

data class WatchSettings(
    val haptics: Boolean = true,
    /** true: la pantalla se atenúa al bajar la muñeca; false: se mantiene encendida mientras graba. */
    val dimWhileRecording: Boolean = true,
)

@Singleton
class WatchSettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val haptics = booleanPreferencesKey("haptics")
        val dim = booleanPreferencesKey("dim_while_recording")
    }

    val settings: Flow<WatchSettings> = context.watchStore.data.map { p ->
        WatchSettings(haptics = p[Keys.haptics] ?: true, dimWhileRecording = p[Keys.dim] ?: true)
    }

    suspend fun setHaptics(value: Boolean) {
        context.watchStore.edit { it[Keys.haptics] = value }
    }

    suspend fun setDimWhileRecording(value: Boolean) {
        context.watchStore.edit { it[Keys.dim] = value }
    }
}
