package com.onvexa.recorder.app.transcription

import android.content.Context
import com.onvexa.recorder.core.audio.MediaFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ModelSpec(val id: String, val sha256: String)
data class ModelStatus(val downloading: Boolean = false, val bytes: Long = 0, val error: String? = null, val ready: Set<String> = emptySet())
@Singleton
class ModelRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private val root = File(context.filesDir,"speech-models").apply { mkdirs() }
    private val specs = mapOf(
        "es" to ModelSpec("vosk-model-small-es-0.42","09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f"),
        "en" to ModelSpec("vosk-model-small-en-us-0.15","30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"),
        "spk" to ModelSpec("vosk-model-spk-0.4","a74d8f51144484813e16af689bb0f916b7a111e2347f467c4933c1166097b5a7"),
    )
    private val mutable = MutableStateFlow(ModelStatus(ready = readyLanguages()))
    val status = mutable.asStateFlow()
    fun directory(language: String): File = File(root,specs.getValue(language).id)
    fun isReady(language: String) = File(directory(language),".complete").isFile
    private fun readyLanguages() = specs.keys.filter { isReady(it) }.toSet()
    suspend fun install(language: String) = mutex.withLock { withContext(Dispatchers.IO) {
        require(language in listOf("es","en"))
        mutable.value = ModelStatus(downloading = true,ready = readyLanguages())
        try {
            for (key in listOf(language,"spk")) {
                if (isReady(key)) continue
                val spec = specs.getValue(key)
                val staging = File(root,"staging-${UUID.randomUUID()}").apply { mkdirs() }
                val archive = File(staging,"model.zip")
                try {
                    val conn = URL("https://alphacephei.com/vosk/models/${spec.id}.zip").openConnection() as HttpURLConnection
                    try {
                        conn.instanceFollowRedirects = false; conn.connectTimeout = 15000; conn.readTimeout = 60000
                        check(conn.responseCode == 200) { "model_download_failed" }
                        conn.inputStream.use { input -> archive.outputStream().use { output ->
                            val bytes = ByteArray(65536); var total = 0L
                            while (true) { currentCoroutineContext().ensureActive(); val n = input.read(bytes); if (n < 0) break
                                total += n; check(total <= 100L * 1024 * 1024); output.write(bytes,0,n)
                                mutable.update { it.copy(bytes = total) }
                            }
                        } }
                    } finally { conn.disconnect() }
                    check(MediaFiles.sha256(archive) == spec.sha256) { "model_integrity_failed" }
                    val extracted = File(staging,"extracted").apply { mkdirs() }
                    var unpacked = 0L
                    ZipInputStream(archive.inputStream()).use { zip ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val entry = zip.nextEntry ?: break
                            val target = MediaFiles.privateFile(extracted,File(extracted,entry.name).path)
                            if (entry.isDirectory) target.mkdirs() else {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { output ->
                                    val bytes = ByteArray(65536)
                                    while (true) { val n = zip.read(bytes); if (n < 0) break; unpacked += n
                                        check(unpacked <= 500L * 1024 * 1024) { "model_archive_too_large" }; output.write(bytes,0,n)
                                    }
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                    val model = File(extracted,spec.id)
                    check(model.isDirectory)
                    File(model,".complete").writeText(spec.sha256)
                    val destination = directory(key)
                    if (destination.exists()) { check(destination.canonicalPath.startsWith(root.canonicalPath + File.separator)); destination.deleteRecursively() }
                    check(model.renameTo(destination)) { "model_install_failed" }
                } finally { check(staging.canonicalPath.startsWith(root.canonicalPath + File.separator)); staging.deleteRecursively() }
            }
            mutable.value = ModelStatus(ready = readyLanguages())
        } catch (e: CancellationException) { mutable.value = ModelStatus(ready = readyLanguages()); throw e }
        catch (e: Exception) { mutable.value = ModelStatus(ready = readyLanguages(),error = "model_install_failed"); throw e }
    } }
}
