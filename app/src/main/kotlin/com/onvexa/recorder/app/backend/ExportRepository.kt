package com.onvexa.recorder.app.backend

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Singleton
class ExportRepository @Inject constructor(@ApplicationContext private val context: Context, private val local: SyncLocalStore) {
    /** Solo archivos referenciados por esta biblioteca y dentro del almacenamiento privado. */
    suspend fun deleteLocalFiles() = withContext(Dispatchers.IO) {
        for ((table, field) in listOf("recordings" to "localAudioPath", "attachments" to "localImagePath")) {
            for (row in local.rows(table)) {
                val path = row[field]?.jsonPrimitive?.contentOrNull ?: continue
                val file = File(path).canonicalFile
                if (file.isFile && file.path.startsWith(context.filesDir.canonicalPath + File.separator)) {
                    check(file.delete()) { "local_file_delete_failed" }
                }
            }
        }
        check(context.getSharedPreferences("storage_uploads", Context.MODE_PRIVATE).edit().clear().commit())
    }
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val rows = SyncCodec.tables.associateWith { JsonArray(local.rows(it)) }
        val output = context.contentResolver.openOutputStream(uri) ?: error("export_unavailable")
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(JsonObject(rows).toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
            for ((table, field, folder) in listOf(Triple("recordings","localAudioPath","audio"), Triple("attachments","localImagePath","attachments"))) {
                for (rowValue in rows.getValue(table)) {
                    val row = rowValue.jsonObject
                    val path = row[field]?.jsonPrimitive?.contentOrNull ?: continue
                    val file = File(path).canonicalFile
                    if (!file.isFile || !file.path.startsWith(context.filesDir.canonicalPath + File.separator)) continue
                    val id = row.getValue("id").jsonPrimitive.content
                    if (!id.matches(Regex("[a-zA-Z0-9_-]+"))) continue
                    zip.putNextEntry(ZipEntry("$folder/$id.${file.extension.takeIf { it.matches(Regex("[a-zA-Z0-9]+")) } ?: "bin"}"))
                    file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        }
    }
}
