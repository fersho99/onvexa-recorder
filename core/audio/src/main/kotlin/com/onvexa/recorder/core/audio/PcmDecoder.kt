package com.onvexa.recorder.core.audio

import android.media.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Convierte el audio del codec a PCM mono 16 kHz sin cargar toda la grabacion en memoria. */
object PcmDecoder {
    suspend fun decode(file: File, consume: (ByteArray) -> Unit, progress: (Long) -> Unit = {}) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("audio_track_missing")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME))); decoder = codec
            codec.configure(format,null,null,0); codec.start()
            var resampler = PcmResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            var inputEnded = false; var ended = false; var idle = 0
            val info = MediaCodec.BufferInfo()
            while (!ended) {
                currentCoroutineContext().ensureActive()
                var moved = false
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val input = checkNotNull(codec.getInputBuffer(index)); input.clear()
                        val size = extractor.readSampleData(input,0)
                        if (size < 0) { codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                        else { codec.queueInputBuffer(index,0,size,extractor.sampleTime,0); extractor.advance() }
                        moved = true
                    }
                }
                when (val output = codec.dequeueOutputBuffer(info,10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) require(out.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_16BIT)
                        resampler = PcmResampler(out.getInteger(MediaFormat.KEY_SAMPLE_RATE),out.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    }
                    else -> if (output >= 0) {
                        try {
                            if (info.size > 0) {
                                val buffer = checkNotNull(codec.getOutputBuffer(output))
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size); buffer.get(bytes)
                                val pcm = resampler.convert(bytes)
                                if (pcm.isNotEmpty()) consume(pcm)
                                progress(info.presentationTimeUs / 1000)
                            }
                            ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            moved = true
                        } finally { codec.releaseOutputBuffer(output,false) }
                    }
                }
                idle = if (moved) 0 else idle + 1
                check(idle < 1500) { "audio_decoder_stalled" }
            }
        } finally { runCatching { decoder?.stop() }; decoder?.release(); extractor.release() }
    }
}

/** Mezcla canales y remuestrea con fase conservada entre buffers, incluyendo frames parciales. */
class PcmResampler(private val rate: Int, private val channels: Int, private val target: Int = 16000) {
    init { require(rate in 8000..192000 && channels in 1..8 && target > 0) }
    private var phase = 0L
    private var pending = ByteArray(0)
    fun convert(bytes: ByteArray): ByteArray {
        val input = pending + bytes; val stride = channels * 2
        val frames = input.size / stride
        val result = ByteArrayOutputStream()
        for (frame in 0 until frames) {
            var sum = 0
            for (ch in 0 until channels) {
                val index = frame * stride + ch * 2
                sum += (((input[index + 1].toInt() and 255) shl 8) or (input[index].toInt() and 255)).toShort().toInt()
            }
            val value = (sum / channels).coerceIn(-32768,32767)
            phase += target
            while (phase >= rate) { result.write(value and 255); result.write((value shr 8) and 255); phase -= rate }
        }
        pending = input.copyOfRange(frames * stride,input.size)
        return result.toByteArray()
    }
}
