package com.onvexa.recorder.app.backend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.onvexa.recorder.core.audio.MediaFiles
import com.onvexa.recorder.core.database.AppDatabase
import com.onvexa.recorder.core.database.entity.AttachmentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import com.onvexa.recorder.app.BuildConfig

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context, private val db: AppDatabase,
    private val auth: AuthRepository, private val local: SyncLocalStore,
) {
    private val mutex = Mutex()
    suspend fun removePurgedFiles(table: String, key: String) = mutex.withLock { withContext(Dispatchers.IO) {
        val paths = mutableListOf<String>()
        if (table == "recordings") {
            db.recordingDao().getById(key)?.localAudioPath?.let(paths::add)
            local.rows("attachments").filter { it["recordingId"]?.jsonPrimitive?.content == key }.forEach { it["localImagePath"]?.jsonPrimitive?.contentOrNull?.let(paths::add) }
        } else if (table == "attachments") local.rows("attachments").firstOrNull { it["id"]?.jsonPrimitive?.content == key }?.get("localImagePath")?.jsonPrimitive?.contentOrNull?.let(paths::add)
        for (path in paths) MediaFiles.privateFile(context.filesDir,path).let { if (it.exists()) check(it.delete()) }
    } }
    suspend fun audio(id: String): File = mutex.withLock { withContext(Dispatchers.IO) {
        val row = db.recordingDao().getById(id) ?: error("recording_not_found")
        row.localAudioPath?.let { MediaFiles.privateFile(context.filesDir,it).takeIf(File::isFile)?.let { return@withContext it } }
        val path = row.remoteAudioPath ?: error("audio_not_available")
        val hash = row.audioSha256?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: error("audio_integrity_missing")
        val file = download("audio",path,hash,52_428_800)
        val current = local.rows("recordings").first { it.getValue("id").jsonPrimitive.content == id }
        local.updateFile("recordings",current,buildJsonObject { put("localAudioPath",file.path) })
        file
    } }
    suspend fun image(id: String): File = mutex.withLock { withContext(Dispatchers.IO) {
        val row = local.rows("attachments").first { it.getValue("id").jsonPrimitive.content == id }
        row["localImagePath"]?.jsonPrimitive?.contentOrNull?.let { MediaFiles.privateFile(context.filesDir,it).takeIf(File::isFile)?.let { return@withContext it } }
        val path = row["remoteImagePath"]?.jsonPrimitive?.contentOrNull ?: error("image_not_available")
        val file = download("attachments",path,null,26_214_400)
        local.updateFile("attachments",row,buildJsonObject { put("localImagePath",file.path) })
        file
    } }
    private suspend fun download(bucket: String, path: String, expectedHash: String?, limit: Long): File {
        val user = auth.account.value ?: throw BackendException(401,"sign_in_required")
        require(path.startsWith(user.id + "/") && !path.contains(".."))
        val folder = File(context.filesDir,"downloads").apply { mkdirs() }
        val extension = path.substringAfterLast('.',"bin").takeIf { it.matches(Regex("[a-zA-Z0-9]{1,5}")) } ?: "bin"
        val file = File(folder,"${UUID.nameUUIDFromBytes(path.toByteArray())}.$extension")
        if (file.isFile && expectedHash != null && MediaFiles.sha256(file) == expectedHash) return file
        val encoded = path.split('/').joinToString("/") { URLEncoder.encode(it,"UTF-8").replace("+","%20") }
        val conn = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/object/authenticated/$bucket/$encoded").openConnection() as HttpURLConnection
        val temp = File.createTempFile("download-",".part",folder)
        try {
            conn.instanceFollowRedirects = false; conn.connectTimeout = 15000; conn.readTimeout = 120000
            conn.setRequestProperty("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            conn.setRequestProperty("Authorization","Bearer ${auth.token()}")
            if (conn.responseCode != 200) throw BackendException(conn.responseCode,"download_failed")
            conn.inputStream.use { input -> temp.outputStream().use { output ->
                val bytes = ByteArray(65536); var total = 0L
                while (true) { val n = input.read(bytes); if (n < 0) break; total += n
                    if (total > limit) throw BackendException(413,"download_too_large")
                    output.write(bytes,0,n)
                }
            } }
            if (expectedHash != null && MediaFiles.sha256(temp) != expectedHash) throw BackendException(502,"download_hash_mismatch")
            if (auth.account.value?.id != user.id) throw BackendException(401,"session_changed")
            java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            return file
        } finally { conn.disconnect(); if (temp.exists()) temp.delete() }
    }
    suspend fun addImage(recordingId: String, positionMs: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val row = db.recordingDao().getById(recordingId) ?: error("recording_not_found")
        require(row.deletedAt == null && positionMs >= 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth in 1..40000 && bounds.outHeight in 1..40000) { "unsupported_image" }
        var sample = 1; while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("image_decode_failed")
        val degrees = runCatching { context.contentResolver.openInputStream(uri)?.use { input ->
            when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
            }
        } ?: 0f }.getOrDefault(0f)
        val rotated = if (degrees == 0f) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply { postRotate(degrees) },true)
        val id = UUID.randomUUID().toString(); val folder = File(context.filesDir,"attachments").apply { mkdirs() }; val file = File(folder,"$id.jpg")
        try { file.outputStream().use { check(rotated.compress(Bitmap.CompressFormat.JPEG,85,it)) } }
        finally { if (rotated !== bitmap) rotated.recycle(); bitmap.recycle() }
        val now = System.currentTimeMillis()
        db.attachmentDao().upsert(AttachmentEntity(id,recordingId,file.path,null,"image/jpeg",positionMs,null,now,now))
        if (uri.authority == context.packageName + ".photos") runCatching { context.contentResolver.delete(uri,null,null) }
        id
    }
}
