package com.onvexa.recorder.app.transcription

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.onvexa.recorder.app.backend.*
import com.onvexa.recorder.app.settings.SettingsRepository
import com.onvexa.recorder.core.audio.*
import com.onvexa.recorder.core.database.AppDatabase
import com.onvexa.recorder.core.database.entity.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel

@Singleton
class ProcessingRepository @Inject constructor(
    @ApplicationContext private val context: Context, private val db: AppDatabase,
    private val models: ModelRepository, private val media: MediaRepository,
    private val settings: SettingsRepository, private val ai: AiRepository,
    private val auth: AuthRepository, private val scheduler: SyncScheduler,
) {
    private val mutex = Mutex()
    suspend fun <T> whilePaused(block: suspend () -> T): T = mutex.withLock { block() }
    suspend fun enqueue(id: String, replace: Boolean = false) {
        if (!MediaFiles.uuid(id)) return
        val prefs = settings.settings.first()
        WorkManager.getInstance(context).enqueueUniqueWork("transcribe-$id",
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<TranscriptionWorker>().setInputData(workDataOf("id" to id))
                .addTag("local-processing")
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).setRequiresCharging(prefs.processOnlyWhileCharging).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS).build())
    }
    suspend fun enqueueAll() { db.recordingDao().getPendingProcessing().filterNot { it.id.startsWith("demo-") }.forEach { enqueue(it.id) } }
    fun downloadModels(language: String) {
        WorkManager.getInstance(context).enqueueUniqueWork("models-$language",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ModelDownloadWorker>().setInputData(workDataOf("language" to language))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresStorageNotLow(true).build()).build())
    }
    suspend fun transcribe(id: String, attempt: Int): Boolean = mutex.withLock { withContext(Dispatchers.IO) {
        val row = db.recordingDao().getById(id) ?: return@withContext true
        if (row.deletedAt != null || row.status == "recording") return@withContext true
        val language = row.language?.takeIf { it in listOf("es","en") } ?: "es"
        val jobId = stable(id,"transcription")
        val now = System.currentTimeMillis()
        fun job(status: String, error: String? = null) = ProcessingJobEntity(jobId,id,"transcription",status,attempt,5,error,now,now,now)
        if (!models.isReady(language) || !models.isReady("spk")) {
            db.jobDao().upsert(job("queued","speech_models_required")); return@withContext false
        }
        db.jobDao().upsert(job("running"))
        db.recordingDao().setStatus(id,"transcribing",now)
        try {
            val file = media.audio(id)
            val previousSpeakers = db.transcriptDao().getSpeakers(id).filter { it.deletedAt == null }.associateBy { it.label }
            // Una retranscripcion es una revision nueva: no reutilizar IDs ya borrados en la nube.
            val revision = UUID.randomUUID().toString()
            val speakers = linkedMapOf<String,SpeakerEntity>()
            val segments = mutableListOf<TranscriptSegmentEntity>()
            val clusters = SpeakerClusters()
            var decodedMs = 0L; var lastEnd = 0L
            val terms = db.vocabularyDao().observeAll().first().map { it.term }
            fun storeResult(raw: String) {
                val result = Json.parseToJsonElement(raw).jsonObject
                var text = result["text"]?.jsonPrimitive?.content.orEmpty().trim()
                if (text.isEmpty()) return
                // Mantiene grafia/capitalizacion del vocabulario cuando coincide una expresion reconocida.
                for (term in terms.sortedByDescending { it.length }) {
                    if (term.isNotBlank()) text = text.replace(Regex("(?iu)(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])"),term)
                }
                val words = result["result"]?.jsonArray.orEmpty()
                val start = words.firstOrNull()?.jsonObject?.get("start")?.jsonPrimitive?.doubleOrNull?.times(1000)?.toLong() ?: lastEnd
                val end = words.lastOrNull()?.jsonObject?.get("end")?.jsonPrimitive?.doubleOrNull?.times(1000)?.toLong() ?: decodedMs
                val embedding = result["spk"]?.jsonArray?.map { it.jsonPrimitive.double }?.toDoubleArray()
                val speaker = embedding?.let { clusters.assign(it,result["spk_frames"]?.jsonPrimitive?.intOrNull ?: 0) }
                val label = "${if (language == "en") "Speaker" else "Hablante"} ${(speaker ?: 0) + 1}"
                val previous = previousSpeakers[label]
                val speakerId = speaker?.let { previous?.id ?: stable(revision,"speaker:$it") }
                if (speakerId != null) speakers.putIfAbsent(speakerId,SpeakerEntity(speakerId,id,label,previous?.displayName,
                    previous?.createdAt ?: now,now))
                val segmentId = stable(revision,"segment:${segments.size}")
                val startMs = start.coerceIn(0,row.durationMs.coerceAtLeast(0))
                val endMs = end.coerceIn(startMs,row.durationMs.coerceAtLeast(startMs))
                segments.add(TranscriptSegmentEntity(segmentId,id,speakerId,segments.size,startMs,endMs,text,language,
                    now,now))
                lastEnd = endMs
            }
            Model(models.directory(language).path).use { model ->
                SpeakerModel(models.directory("spk").path).use { speakerModel ->
                    Recognizer(model,16000f,speakerModel).use { recognizer ->
                        recognizer.setWords(true)
                        PcmDecoder.decode(file,consume = { bytes ->
                            decodedMs += bytes.size / 32
                            if (recognizer.acceptWaveForm(bytes,bytes.size)) storeResult(recognizer.result)
                        })
                        storeResult(recognizer.finalResult)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            db.withTransaction {
                val current = db.recordingDao().getById(id)
                if (current == null || current.deletedAt != null) return@withTransaction
                db.transcriptDao().commitTranscript(id,speakers.values.toList(),segments,now)
                db.recordingDao().setStatus(id,"ready",System.currentTimeMillis())
                db.jobDao().upsert(job("succeeded"))
            }
            if (auth.syncEnabled && auth.account.value != null) scheduler.schedule()
            if (settings.settings.first().autoAi && segments.isNotEmpty()) enqueueAi(id)
            true
        } catch (e: CancellationException) {
            withContext(NonCancellable) { db.recordingDao().setStatus(id,"recorded",System.currentTimeMillis()); db.jobDao().upsert(job("queued","processing_interrupted")) }
            throw e
        } catch (e: Exception) {
            db.recordingDao().setStatus(id,"recorded",System.currentTimeMillis())
            db.jobDao().upsert(job("failed",if (e is BackendException) e.code else "transcription_failed"))
            throw e
        }
    } }
    private suspend fun enqueueAi(id: String) {
        val prefs = settings.settings.first()
        WorkManager.getInstance(context).enqueueUniqueWork("automatic-ai-$id",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AutomaticAiWorker>().setInputData(workDataOf("id" to id))
                .addTag("local-processing")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (prefs.syncWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()).build())
    }
    suspend fun automaticAi(id: String) {
        if (!settings.settings.first().autoAi || auth.account.value == null || !auth.syncEnabled) return
        val now = System.currentTimeMillis(); val jobId = stable(id,"automatic-ai")
        db.jobDao().upsert(ProcessingJobEntity(jobId,id,"summary","running",1,3,null,now,now,now))
        try {
            // No volver a generar un resumen completado si solo falto el mapa.
            if (db.summaryDao().observeLatestSummary(id).first() == null) ai.summarize(id)
            if (db.summaryDao().observeLatestMindMap(id).first() == null) ai.mindMap(id)
            db.jobDao().update(jobId,"succeeded",1,null,now,System.currentTimeMillis())
        } catch (e: Exception) {
            db.jobDao().update(jobId,"failed",1,(e as? BackendException)?.code ?: "ai_failed",now,System.currentTimeMillis()); throw e
        }
    }
    private fun stable(recording: String, suffix: String) = UUID.nameUUIDFromBytes("$recording:$suffix".toByteArray()).toString()
}
