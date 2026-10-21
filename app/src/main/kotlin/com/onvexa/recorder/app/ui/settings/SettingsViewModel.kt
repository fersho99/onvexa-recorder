package com.onvexa.recorder.app.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onvexa.recorder.app.data.RecordingsRepository
import com.onvexa.recorder.app.data.TemplateBootstrap
import com.onvexa.recorder.app.demo.DemoSeeder
import com.onvexa.recorder.app.settings.AppSettings
import com.onvexa.recorder.app.settings.SettingsRepository
import com.onvexa.recorder.core.database.entity.TemplateEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val recordings: RecordingsRepository,
    private val templateBootstrap: TemplateBootstrap,
    private val demo: DemoSeeder,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val templates: StateFlow<List<TemplateEntity>> = recordings.templates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val demoAvailable: Boolean get() = demo.available

    /** El cambio de idioma por app lo aplica el sistema desde Android 13; antes se usa el del sistema. */
    val languagePerAppSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun setLanguage(value: String) = viewModelScope.launch {
        settingsRepository.setLanguage(value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (value == "auto") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(value)
            context.getSystemService(LocaleManager::class.java).applicationLocales = locales
        }
    }

    fun setDefaultTemplate(id: String) = viewModelScope.launch { settingsRepository.setDefaultTemplate(id) }
    fun setSyncWifiOnly(value: Boolean) = viewModelScope.launch { settingsRepository.setSyncWifiOnly(value) }
    fun setProcessOnlyWhileCharging(value: Boolean) =
        viewModelScope.launch { settingsRepository.setProcessOnlyWhileCharging(value) }

    fun deleteAll() = viewModelScope.launch {
        recordings.deleteAllLocalData()
        templateBootstrap.ensure()
    }

    fun seedDemo() = viewModelScope.launch { demo.seed() }
    fun clearDemo() = viewModelScope.launch { demo.clear() }
}
