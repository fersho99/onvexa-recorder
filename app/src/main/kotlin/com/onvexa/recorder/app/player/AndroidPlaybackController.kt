package com.onvexa.recorder.app.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.onvexa.recorder.app.backend.MediaRepository
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Singleton
class AndroidPlaybackController @Inject constructor(private val media: MediaRepository) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(PlaybackState())
    override val state = mutable.asStateFlow()
    private var player: MediaPlayer? = null
    private var loading: Job? = null
    private var ticker: Job? = null
    override fun load(recordingId: String, durationMs: Long) {
        if (state.value.recordingId == recordingId && state.value.error == null) return
        loading?.cancel(); ticker?.cancel(); player?.release(); player = null
        mutable.value = PlaybackState(recordingId = recordingId,durationMs = durationMs,loading = true)
        loading = scope.launch {
            try {
                val file = media.audio(recordingId)
                ensureActive()
                val loaded = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build())
                    setDataSource(file.path)
                    setOnPreparedListener { if (state.value.recordingId == recordingId) mutable.update { it.copy(loading = false,durationMs = duration.toLong()) } }
                    setOnCompletionListener { mutable.update { it.copy(isPlaying = false,positionMs = it.durationMs) } }
                    setOnErrorListener { _, _, _ -> mutable.update { it.copy(isPlaying = false,loading = false,error = "audio_playback_failed") }; true }
                }
                player = loaded
                loaded.prepareAsync()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(loading = false,error = "audio_unavailable") } }
        }
    }
    override fun play() {
        if (state.value.loading || state.value.error != null) return
        val current = player ?: return
        runCatching {
            if (state.value.positionMs >= state.value.durationMs) current.seekTo(0)
            current.start(); mutable.update { it.copy(isPlaying = true) }
            ticker?.cancel(); ticker = scope.launch {
                while (isActive && current.isPlaying) { mutable.update { it.copy(positionMs = current.currentPosition.toLong()) }; delay(100) }
            }
        }.onFailure { mutable.update { it.copy(error = "audio_playback_failed",isPlaying = false) } }
    }
    override fun pause() { ticker?.cancel(); runCatching { player?.pause() }; mutable.update { it.copy(isPlaying = false) } }
    override fun seekTo(positionMs: Long) {
        val value = positionMs.coerceIn(0,state.value.durationMs)
        if (!state.value.loading) runCatching { player?.seekTo(value,MediaPlayer.SEEK_CLOSEST) }
        mutable.update { it.copy(positionMs = value) }
    }
    override fun skip(deltaMs: Long) = seekTo(state.value.positionMs + deltaMs)
    override fun setSpeed(speed: Float) {
        require(speed in 0.5f..2f)
        if (!state.value.loading) runCatching { player?.let {
            val wasPlaying = it.isPlaying
            it.playbackParams = it.playbackParams.setSpeed(speed)
            if (!wasPlaying) it.pause()
        } }
        mutable.update { it.copy(speed = speed) }
    }
}
@Module @InstallIn(SingletonComponent::class)
abstract class PlayerModule { @Binds abstract fun bindPlayback(impl: AndroidPlaybackController): PlaybackController }
