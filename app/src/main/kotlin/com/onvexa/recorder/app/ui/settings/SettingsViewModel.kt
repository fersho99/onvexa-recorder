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
import kotlinx.coroutines.CancellationException
import androidx.work.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.onvexa.recorder.app.backend.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val recordings: RecordingsRepository,
    private val templateBootstrap: TemplateBootstrap,
    private val demo: DemoSeeder,
    private val auth: AuthRepository,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
    private val http: BackendHttp,
    private val exporter: ExportRepository,
    private val models: com.onvexa.recorder.app.transcription.ModelRepository,
    private val processing: com.onvexa.recorder.app.transcription.ProcessingRepository,
    private val db: com.onvexa.recorder.core.database.AppDatabase,
    private val transfer: com.onvexa.recorder.core.audio.WearTransfer,
) : ViewModel() {
    val modelStatus = models.status
    val transferStatus = transfer.status
    val vocabulary = db.vocabularyDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun downloadModel(language: String) = processing.downloadModels(language)
    fun syncWatch() = transfer.schedule()
    fun setAutomaticAi(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoAi(enabled) }
    fun addTerm(term: String) = viewModelScope.launch {
        val value = term.trim().take(200)
        if (value.isNotEmpty()) {
            val now = System.currentTimeMillis()
            db.vocabularyDao().upsert(com.onvexa.recorder.core.database.entity.VocabularyTermEntity(java.util.UUID.randomUUID().toString(),value,now,now))
            if (auth.syncEnabled) scheduler.schedule()
        }
    }
    fun deleteTerm(id: String) = viewModelScope.launch { db.vocabularyDao().softDelete(id,System.currentTimeMillis()); if (auth.syncEnabled) scheduler.schedule() }
    val account = auth.account
    val syncStatus = sync.status
    val backendConfigured get() = auth.configured
    private val _accountStatus = MutableStateFlow(AccountUiState())
    val accountStatus = _accountStatus.asStateFlow()
    private val _aiPreview = MutableStateFlow<String?>(null)
    val aiPreview = _aiPreview.asStateFlow()

    /** Prueba manual, autenticada, sin grabaciones reales ni consentimiento de subida de biblioteca. */
    fun testAi() = accountAction {
        val request = buildJsonObject {
            put("task", "summary")
            put("language", if (java.util.Locale.getDefault().language == "en") "en" else "es")
            put("transcript", buildJsonArray {
                add(buildJsonObject {
                    put("seq", 0); put("startMs", 0); put("endMs", 9000)
                    put("text", "Ejemplo ficticio: El equipo escolar acordo probar el inicio de sesion y preparar una demostracion de la grabadora. Alex revisara la conexion entre el reloj y el celular. No se fijaron fechas.")
                })
            })
        }
        val response = http.request("POST", "/functions/v1/ai", request, auth.token(),
            mapOf("Idempotency-Key" to java.util.UUID.randomUUID().toString())).jsonObject
        _aiPreview.value = response["executive"]?.jsonPrimitive?.contentOrNull?.take(600)
            ?: throw BackendException(502, "invalid_provider_output")
        "ai_test_success"
    }

    fun signIn(email: String, password: String) = accountAction { auth.signIn(email, password); "signed_in" }
    fun signUp(email: String, password: String) = accountAction {
        if (auth.signUp(email, password)) "confirm_email" else "signed_in"
    }
    fun enableSync() = accountAction { auth.enableSync(); scheduler.schedule(); "sync_scheduled" }
    fun signOut() = accountAction {
        scheduler.cancel()
        sync.whilePaused { auth.signOut() }
        "signed_out"
    }
    fun export(uri: android.net.Uri) = accountAction {
        sync.whilePaused { exporter.export(uri) }; "export_done"
    }
    fun deleteAccount() = accountAction {
        check(com.onvexa.recorder.core.audio.CaptureRuntime.state.value.phase == com.onvexa.recorder.core.audio.CapturePhase.Idle) { "recording_in_progress" }
        androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag("local-processing").await()
        scheduler.cancel()
        processing.whilePaused { sync.whilePaused {
            auth.disableSync()
            http.request("POST", "/functions/v1/delete-account", buildJsonObject { put("confirm", "DELETE_MY_ACCOUNT") }, auth.token())
            auth.clearAfterAccountDeletion()
            exporter.deleteLocalFiles()
            recordings.deleteAllLocalData()
            templateBootstrap.ensure()
            auth.forgetLocalOwner()
        } }
        "account_deleted"
    }
    private fun accountAction(action: suspend () -> String) = viewModelScope.launch {
        if (_accountStatus.value.busy) return@launch
        _aiPreview.value = null
        _accountStatus.value = AccountUiState(busy = true)
        try { _accountStatus.value = AccountUiState(message = action()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _accountStatus.value = AccountUiState(error = if (e is BackendException) e.code else "connection_failed") }
    }

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
    fun setSyncWifiOnly(value: Boolean) = viewModelScope.launch {
        settingsRepository.setSyncWifiOnly(value)
        if (auth.account.value != null && auth.syncEnabled) scheduler.schedule()
    }
    fun setProcessOnlyWhileCharging(value: Boolean) =
        viewModelScope.launch { settingsRepository.setProcessOnlyWhileCharging(value) }

    fun deleteAll() = accountAction {
        check(com.onvexa.recorder.core.audio.CaptureRuntime.state.value.phase == com.onvexa.recorder.core.audio.CapturePhase.Idle) { "recording_in_progress" }
        androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag("local-processing").await()
        scheduler.cancel()
        processing.whilePaused { sync.whilePaused {
            auth.disableSync()
            // Cierre local aunque no haya red: signOut limpia el token en finally.
            try { auth.signOut() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Sesion local ya limpiada. */ }
            exporter.deleteLocalFiles()
            recordings.deleteAllLocalData()
            templateBootstrap.ensure()
            auth.forgetLocalOwner()
        } }
        "local_deleted"
    }

    fun seedDemo() = viewModelScope.launch { demo.seed() }
    fun clearDemo() = viewModelScope.launch { demo.clear() }
}

data class AccountUiState(val busy: Boolean = false, val message: String? = null, val error: String? = null)
