package com.onvexa.recorder.app.backend

import android.content.Context
import android.util.Base64
import com.onvexa.recorder.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UploadedFile(val path: String, val bytes: Long, val sha256: String)

@Singleton
class StorageTransfer @Inject constructor(@ApplicationContext private val context: Context, private val auth: AuthRepository) {
    private val prefs = context.getSharedPreferences("storage_uploads", Context.MODE_PRIVATE)
    suspend fun upload(bucket: String, path: String, localPath: String, mime: String): UploadedFile = withContext(Dispatchers.IO) {
        val user = auth.account.value ?: throw BackendException(401, "sign_in_required")
        require(bucket in listOf("audio", "attachments") && path.startsWith(user.id + "/"))
        val file = File(localPath).canonicalFile
        // Nunca permitir que un registro local haga subir archivos externos a la app.
        require(file.path.startsWith(context.filesDir.canonicalPath + File.separator)) { "file_outside_app_storage" }
        require(file.isFile) { "file_not_found" }
        val bytes = file.length(); val modified = file.lastModified()
        if (bytes > if (bucket == "audio") 52_428_800 else 26_214_400) throw BackendException(413, "file_too_large")
        val sha = sha256(file)
        val fingerprint = "$bucket:$path:$sha"
        var location = prefs.getString(fingerprint, null)
        var offset = 0L
        if (location != null) {
            val head = open(location, "HEAD", auth.token())
            try {
                when (head.responseCode) {
                    200, 204 -> offset = head.getHeaderField("Upload-Offset")?.toLongOrNull() ?: 0
                    404, 410 -> location = null
                    else -> throw BackendException(head.responseCode, "upload_resume_failed")
                }
            } finally { head.disconnect() }
        }
        if (location == null) {
            val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/upload/resumable"
            val post = open(endpoint, "POST", auth.token())
            try {
                post.setRequestProperty("Upload-Length", bytes.toString())
                post.setRequestProperty("x-upsert", "true")
                post.setRequestProperty("Upload-Metadata", mapOf("bucketName" to bucket, "objectName" to path,
                    "contentType" to mime, "cacheControl" to "3600").entries.joinToString(",") {
                    it.key + " " + Base64.encodeToString(it.value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                })
                post.doOutput = true
                post.setFixedLengthStreamingMode(0)
                post.outputStream.close()
                if (post.responseCode != 201) throw BackendException(post.responseCode, "upload_create_failed")
                location = URI(endpoint).resolve(post.getHeaderField("Location") ?: error("upload_location_missing")).toString()
                validateLocation(location!!)
                check(prefs.edit().putString(fingerprint, location).commit())
            } finally { post.disconnect() }
        }
        require(offset in 0..bytes) { "invalid_upload_offset" }
        RandomAccessFile(file, "r").use { input ->
            val buffer = ByteArray(6 * 1024 * 1024)
            while (offset < bytes) {
                if (auth.account.value?.id != user.id) throw BackendException(401, "session_changed")
                input.seek(offset)
                val count = minOf(buffer.size.toLong(), bytes - offset).toInt()
                input.readFully(buffer, 0, count)
                val patch = open(location!!, "PATCH", auth.token())
                try {
                    patch.setRequestProperty("Upload-Offset", offset.toString())
                    patch.setRequestProperty("Content-Type", "application/offset+octet-stream")
                    patch.doOutput = true
                    patch.setFixedLengthStreamingMode(count)
                    patch.outputStream.use { it.write(buffer, 0, count) }
                    if (patch.responseCode != 204) throw BackendException(patch.responseCode, "upload_chunk_failed")
                    val next = patch.getHeaderField("Upload-Offset")?.toLongOrNull()
                    if (next != offset + count) throw BackendException(502, "upload_offset_mismatch")
                    offset = next
                } finally { patch.disconnect() }
            }
        }
        if (file.length() != bytes || file.lastModified() != modified || sha256(file) != sha) throw BackendException(409, "file_changed_during_upload")
        UploadedFile(path, bytes, sha)
    }
    private fun validateLocation(location: String) {
        val base = URI(BuildConfig.SUPABASE_URL)
        val uri = URI(location)
        require(uri.scheme == "https" && uri.userInfo == null &&
            uri.host in listOf(base.host, base.host.replace(".supabase.co", ".storage.supabase.co")) &&
            uri.path.startsWith("/storage/v1/upload/resumable/")) { "unsafe_upload_location" }
    }
    private fun open(location: String, method: String, token: String): HttpURLConnection {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/') + "/storage/v1/upload/resumable"
        if (location != base) validateLocation(location)
        return (URL(location).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15_000; readTimeout = 120_000; instanceFollowRedirects = false
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Tus-Resumable", "1.0.0")
        }
    }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
