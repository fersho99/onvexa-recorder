package com.onvexa.recorder.core.audio

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.onvexa.recorder.core.database.AppDatabase
import com.onvexa.recorder.core.database.entity.*
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update

@AndroidEntryPoint
class RecordingService : Service() {
    @Inject lateinit var db: AppDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var recording: RecordingEntity? = null
    private var timer: Job? = null
    private var clock = CaptureClock()
    private var wakeLock: PowerManager.WakeLock? = null
    private var destroyed = false
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CaptureCommands.START -> if (CaptureRuntime.state.value.phase == CapturePhase.Idle) begin(intent)
            CaptureCommands.STOP -> finish()
            CaptureCommands.PAUSE -> control(pausing = true)
            CaptureCommands.RESUME -> control(pausing = false)
            CaptureCommands.MARK -> annotate(null)
            CaptureCommands.NOTE -> intent.getStringExtra("text")?.trim()?.takeIf { it.isNotEmpty() }?.let(::annotate)
        }
        return START_NOT_STICKY // nunca reabrir el microfono sin una accion visible del usuario.
    }
    private fun begin(intent: Intent) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CaptureRuntime.mutable.value = CaptureState(error = "microphone_permission_required"); stopSelf(); return
        }
        CaptureRuntime.mutable.value = CaptureState(phase = CapturePhase.Starting)
        try { showNotification() } catch (_: Exception) {
            CaptureRuntime.mutable.value = CaptureState(error = "cannot_start_microphone_service"); stopSelf(); return
        }
        scope.launch {
            var device: MediaRecorder? = null
            try {
                val id = UUID.randomUUID().toString()
                val audio = File(filesDir, "audio").apply { mkdirs() }
                val path = File(audio, "$id.m4a")
                val now = System.currentTimeMillis()
                val source = intent.getStringExtra("source")?.takeIf { it == "watch" } ?: "phone"
                recording = RecordingEntity(id = id, deviceId = null, folderId = null,
                    templateId = intent.getStringExtra("template"), title = null, source = source,
                    status = RecordingStatus.RECORDING, language = intent.getStringExtra("language") ?: "es",
                    durationMs = 0, startedAt = now, localAudioPath = path.path, remoteAudioPath = null,
                    audioBytes = null, audioSha256 = null, createdAt = now, updatedAt = now)
                db.recordingDao().upsert(checkNotNull(recording))
                // Conserva el instante del aviso aceptado, por grabacion, sin enviar audio.
                withContext(Dispatchers.IO) {
                    db.openHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO sync_metadata (`key`,value) VALUES (?,?)",
                        arrayOf("recording_consent:$id", now.toString()))
                }
                @Suppress("DEPRECATION")
                device = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this@RecordingService) else MediaRecorder()
                check(!destroyed) { "recording_interrupted" }
                device.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioChannels(1); setAudioSamplingRate(16_000); setAudioEncodingBitRate(48_000)
                    setOutputFile(path.path)
                    // Mantiene cada archivo dentro del limite gratuito de Storage, sin borrar la copia local.
                    setMaxFileSize(48L * 1024 * 1024)
                    setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) finish() }
                    setOnErrorListener { _, _, _ -> finish("microphone_error") }
                    prepare(); start()
                }
                recorder = device
                wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "onvexa:recording")
                    .apply { acquire(4 * 60 * 60 * 1000L) }
                clock = CaptureClock().apply { resume(SystemClock.elapsedRealtime()) }
                CaptureRuntime.mutable.value = CaptureState(id = id, phase = CapturePhase.Recording)
                timer = scope.launch {
                    while (isActive) {
                        delay(100)
                        val level = if (CaptureRuntime.state.value.phase == CapturePhase.Paused) 0f
                            else (runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0) / 32768f).coerceIn(0f, 1f)
                        CaptureRuntime.mutable.update { it.copy(elapsedMs = clock.elapsed(SystemClock.elapsedRealtime()), levels = (it.levels + level).takeLast(120)) }
                    }
                }
            } catch (e: Exception) {
                runCatching { device?.release() }; recorder = null
                recording?.let { db.recordingDao().setStatus(it.id, RecordingStatus.FAILED, System.currentTimeMillis()) }
                recording = null
                CaptureRuntime.mutable.value = CaptureState(error = "recording_start_failed")
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        }
    }
    private fun control(pausing: Boolean) {
        val expected = if (pausing) CapturePhase.Recording else CapturePhase.Paused
        if (CaptureRuntime.state.value.phase != expected) return
        try {
            if (pausing) { recorder?.pause(); clock.pause(SystemClock.elapsedRealtime()) }
            else { recorder?.resume(); clock.resume(SystemClock.elapsedRealtime()) }
            CaptureRuntime.mutable.update { it.copy(phase = if (pausing) CapturePhase.Paused else CapturePhase.Recording) }
            showNotification()
        } catch (_: Exception) { finish("recording_control_failed") }
    }
    private fun annotate(text: String?) {
        val s = CaptureRuntime.state.value
        if (s.phase !in listOf(CapturePhase.Recording, CapturePhase.Paused)) return
        val id = s.id ?: return
        scope.launch {
            val now = System.currentTimeMillis(); val ms = clock.elapsed(SystemClock.elapsedRealtime())
            if (text == null) {
                db.bookmarkDao().upsert(BookmarkEntity(UUID.randomUUID().toString(), id, ms, null, now, now))
                CaptureRuntime.mutable.update { if (it.id == id) it.copy(highlights = it.highlights + 1) else it }
            } else {
                db.noteDao().upsert(NoteEntity(UUID.randomUUID().toString(), id, ms, text.take(20_000), now, now))
                CaptureRuntime.mutable.update { if (it.id == id) it.copy(notes = it.notes + 1) else it }
            }
        }
    }
    private fun finish(error: String? = null) {
        if (CaptureRuntime.state.value.phase !in listOf(CapturePhase.Recording, CapturePhase.Paused)) return
        timer?.cancel(); clock.pause(SystemClock.elapsedRealtime())
        val entity = recording ?: return
        CaptureRuntime.mutable.update { it.copy(phase = CapturePhase.Saving, elapsedMs = clock.elapsed(SystemClock.elapsedRealtime())) }
        val duration = CaptureRuntime.state.value.elapsedMs
        var failed = error
        try { recorder?.stop() } catch (_: Exception) { failed = "recording_too_short_or_interrupted" }
        runCatching { recorder?.release() }; recorder = null; recording = null
        if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null
        scope.launch {
            try {
                withContext(Dispatchers.IO + NonCancellable) {
                    val file = File(checkNotNull(entity.localAudioPath))
                    val hash = if (file.exists()) MediaFiles.sha256(file) else null
                    db.recordingDao().update(entity.copy(durationMs = duration, audioBytes = file.takeIf { it.exists() }?.length(),
                        audioSha256 = hash, status = if (failed == null) RecordingStatus.RECORDED else RecordingStatus.FAILED,
                        updatedAt = System.currentTimeMillis()))
                }
                CaptureRuntime.mutable.value = CaptureState(elapsedMs = duration, savedId = entity.id, error = failed)
                if (failed == null) (application as? CaptureCompletionListener)?.onRecordingSaved(entity.id)
            } finally { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
    }
    private fun showNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("capture", "Onvexa · Grabacion", NotificationManager.IMPORTANCE_LOW))
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launch?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction(CaptureCommands.STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val paused = CaptureRuntime.state.value.phase == CapturePhase.Paused
        val notification = NotificationCompat.Builder(this, "capture").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Onvexa").setContentText(if (paused) "Grabacion pausada" else "Grabando audio")
            .setOngoing(true).setContentIntent(contentIntent).addAction(android.R.drawable.ic_media_pause, "Detener y guardar", stop).build()
        ServiceCompat.startForeground(this, 2001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }
    override fun onDestroy() {
        destroyed = true
        if (recorder != null) finish("recording_interrupted")
        timer?.cancel()
        // Las escrituras finales tienen NonCancellable; no eliminar un audio parcial.
        super.onDestroy()
    }
}
