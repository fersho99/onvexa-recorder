package com.onvexa.recorder.app.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

data class AppSettings(
    /** auto | es | en */
    val language: String = "auto",
    val defaultTemplateId: String? = null,
    val syncWifiOnly: Boolean = true,
    val processOnlyWhileCharging: Boolean = false,
    val autoAi: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val language = stringPreferencesKey("language")
        val defaultTemplate = stringPreferencesKey("default_template")
        val wifiOnly = booleanPreferencesKey("sync_wifi_only")
        val charging = booleanPreferencesKey("process_only_charging")
        val autoAi = booleanPreferencesKey("automatic_ai")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            language = p[Keys.language] ?: "auto",
            defaultTemplateId = p[Keys.defaultTemplate],
            syncWifiOnly = p[Keys.wifiOnly] ?: true,
            processOnlyWhileCharging = p[Keys.charging] ?: false,
            autoAi = p[Keys.autoAi] ?: false,
        )
    }

    suspend fun setLanguage(value: String) = edit { it[Keys.language] = value }
    suspend fun setDefaultTemplate(id: String) = edit { it[Keys.defaultTemplate] = id }
    suspend fun setSyncWifiOnly(value: Boolean) = edit { it[Keys.wifiOnly] = value }
    suspend fun setProcessOnlyWhileCharging(value: Boolean) = edit { it[Keys.charging] = value }
    suspend fun setAutoAi(value: Boolean) = edit { it[Keys.autoAi] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit { block(it) }
    }
}
