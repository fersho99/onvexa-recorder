package com.onvexa.recorder.core.audio

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.android.gms.wearable.*
import com.onvexa.recorder.core.database.AppDatabase
import com.onvexa.recorder.core.database.entity.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class TransferStatus(val connectedNodes: Int = 0, val busy: Boolean = false, val error: String? = null)
@Singleton
class WearTransfer @Inject constructor(@ApplicationContext private val context: Context, private val db: AppDatabase) {
    private val mutex = Mutex()
    private val data = Wearable.getDataClient(context)
    private val watch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    private val mutable = MutableStateFlow(TransferStatus())
    val status = mutable.asStateFlow()
    fun schedule(periodic: Boolean = false) {
        val manager = WorkManager.getInstance(context)
        if (periodic) manager.enqueueUniquePeriodicWork("wear-transfer-periodic",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WearTransferWorker>(15,TimeUnit.MINUTES).build())
        manager.enqueueUniqueWork("wear-transfer",ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WearTransferWorker>().setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    }
    private suspend fun items(prefix: String): List<DataItem> {
        val buffer = data.getDataItems(Uri.parse("wear://*$prefix"),DataClient.FILTER_PREFIX).await()
        return try { buffer.map { it.freeze() } } finally { buffer.release() }
    }
    suspend fun synchronize() = mutex.withLock { withContext(Dispatchers.IO) {
        mutable.value = mutable.value.copy(busy = true,error = null)
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            mutable.update { it.copy(connectedNodes = nodes.size) }
            if (watch) {
                for (item in items("/onvexa/acks/")) acknowledge(item)
                for (item in items("/onvexa/results/")) receiveResults(item)
                for (row in db.recordingDao().getPendingSync()) {
                    if (row.source != "watch" || row.status in listOf("recording","failed") || row.deletedAt != null || row.localAudioPath == null || !validId(row.id)) continue
                    val file = MediaFiles.privateFile(context.filesDir,checkNotNull(row.localAudioPath))
                    if (!file.isFile) continue
                    val hash = MediaFiles.sha256(file)
                    val metadata = JSONObject().put("id",row.id).put("sha256",hash).put("bytes",file.length())
                        .put("durationMs",row.durationMs).put("startedAt",row.startedAt).put("language",row.language ?: "es")
                        .put("bookmarks",JSONArray().apply {
                            db.bookmarkDao().observeFor(row.id).first().forEach { put(JSONObject().put("id",it.id).put("ms",it.positionMs).put("label",it.label ?: "")) }
                        }).put("notes",JSONArray().apply {
                            db.noteDao().observeFor(row.id).first().forEach { put(JSONObject().put("id",it.id).put("ms",it.positionMs).put("text",it.text)) }
                        })
                    val bytes = metadata.toString().toByteArray()
                    check(bytes.size <= 8 * 1024 * 1024)
                    val request = PutDataMapRequest.create("/onvexa/recordings/${row.id}")
                    request.dataMap.putAsset("audio",Asset.createFromUri(Uri.fromFile(file)))
                    request.dataMap.putAsset("metadata",Asset.createFromBytes(bytes))
                    request.dataMap.putString("hash",hash)
                    data.putDataItem(request.asPutDataRequest().setUrgent()).await()
                }
                publishActions()
            } else {
                for (item in items("/onvexa/recordings/")) receiveRecording(item)
                for (item in items("/onvexa/actions/")) receiveAction(item)
                publishResults()
            }
            mutable.update { it.copy(busy = false) }
        } catch (e: CancellationException) { mutable.update { it.copy(busy = false) }; throw e }
        catch (e: Exception) { mutable.update { it.copy(busy = false,error = "device_transfer_failed") }; throw e }
    }
    }
    private suspend fun asset(asset: Asset, file: File, limit: Long) {
        val descriptor = data.getFdForAsset(asset).await()
        try {
            val input = descriptor.inputStream ?: error("asset_unavailable")
            input.use { source -> file.outputStream().use { output ->
                val bytes = ByteArray(65536); var total = 0L
                while (true) { val n = source.read(bytes); if (n < 0) break; total += n
                    check(total <= limit) { "asset_too_large" }; output.write(bytes,0,n)
                }
            } }
        } finally { descriptor.release() }
    }
    private suspend fun metadata(map: DataMap, name: String): JSONObject {
        val temp = File.createTempFile("watch-meta-",".json",context.cacheDir)
        return try { asset(checkNotNull(map.getAsset(name)),temp,8L * 1024 * 1024); JSONObject(temp.readText()) } finally { temp.delete() }
    }
    private suspend fun receiveRecording(item: DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        val meta = metadata(map,"metadata")
        val id = meta.getString("id"); require(validId(id) && item.uri.path == "/onvexa/recordings/$id")
        val hash = meta.getString("sha256"); require(hash.matches(Regex("[0-9a-f]{64}")))
        val old = db.recordingDao().getById(id)
        if (wasPurged(id)) { publishDeletion(id); return }
        if (old != null && (old.source != "watch" || old.deletedAt != null)) return
        val folder = File(context.filesDir,"watch-inbox").apply { mkdirs() }; val file = File(folder,"$id.m4a")
        if (!file.isFile || MediaFiles.sha256(file) != hash) {
            val temp = File.createTempFile("watch-audio-",".part",folder)
            try {
                asset(checkNotNull(map.getAsset("audio")),temp,50L * 1024 * 1024)
                check(temp.length() == meta.getLong("bytes") && MediaFiles.sha256(temp) == hash) { "watch_hash_mismatch" }
                java.nio.file.Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { temp.delete() }
        }
        val now = System.currentTimeMillis()
        if (old == null) {
            val duration = meta.getLong("durationMs"); require(duration in 0..24L * 60 * 60 * 1000)
            db.withTransaction {
                db.recordingDao().upsert(RecordingEntity(id,null,null,null,null,"watch","recorded",meta.optString("language","es"),
                    duration,meta.getLong("startedAt"),file.path,null,file.length(),hash,now,now))
                val marks = meta.optJSONArray("bookmarks") ?: JSONArray()
                for (i in 0 until marks.length()) { val mark = marks.getJSONObject(i); require(validId(mark.getString("id")))
                    db.bookmarkDao().upsert(BookmarkEntity(mark.getString("id"),id,mark.getLong("ms").coerceIn(0,duration),mark.optString("label"),now,now)) }
                val notes = meta.optJSONArray("notes") ?: JSONArray()
                for (i in 0 until notes.length()) { val note = notes.getJSONObject(i); require(validId(note.getString("id")))
                    db.noteDao().upsert(NoteEntity(note.getString("id"),id,note.getLong("ms").coerceIn(0,duration),note.getString("text").take(20000),now,now)) }
            }
            (context.applicationContext as? CaptureCompletionListener)?.onRecordingSaved(id)
        } else if (old.localAudioPath?.let { !File(it).isFile } != false) db.recordingDao().update(old.copy(localAudioPath = file.path))
        val ack = PutDataMapRequest.create("/onvexa/acks/$id")
        ack.dataMap.putString("id",id); ack.dataMap.putString("hash",hash)
        data.putDataItem(ack.asPutDataRequest().setUrgent()).await()
    }
    private suspend fun acknowledge(item: DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        val id = map.getString("id") ?: return
        if (!validId(id)) return
        val row = db.recordingDao().getById(id) ?: return
        if (row.source != "watch" || row.audioSha256 == null || row.audioSha256 != map.getString("hash")) return
        if (row.syncState != SyncState.SYNCED) db.recordingDao().update(row.copy(syncState = SyncState.SYNCED))
        val localNode = Wearable.getNodeClient(context).localNode.await()
        data.deleteDataItems(Uri.Builder().scheme("wear").authority(localNode.id).path("/onvexa/recordings/$id").build()).await()
        // Se conserva siempre la copia local del reloj; ACK no borra el archivo.
    }
    private suspend fun publishResults() {
        db.query(SimpleSQLiteQuery("SELECT `key` FROM sync_metadata WHERE `key` LIKE 'purged:recordings:%'")).use { cursor ->
            while (cursor.moveToNext()) publishDeletion(cursor.getString(0).substringAfterLast(':'))
        }
        db.query(SimpleSQLiteQuery("SELECT id FROM recordings WHERE source='watch' AND deletedAt IS NOT NULL")).use { cursor ->
            while (cursor.moveToNext()) publishDeletion(cursor.getString(0))
        }
        val rows = db.recordingDao().observeLibrary(null,null,null).first().filter { it.source == "watch" && validId(it.id) }
        for (row in rows) {
            val summary = db.summaryDao().observeLatestSummary(row.id).first()
            val actions = db.summaryDao().observeActionItems(row.id).first()
            if (summary == null && actions.isEmpty()) continue
            val payload = JSONObject().put("recordingId",row.id).put("title",row.title ?: "")
            if (summary != null) payload.put("summary",JSONObject().put("id",summary.id).put("executive",summary.executive).put("detailed",summary.detailed)
                .put("createdAt",summary.createdAt).put("updatedAt",summary.updatedAt))
            payload.put("actions",JSONArray().apply { actions.forEach { put(JSONObject().put("id",it.id).put("description",it.description)
                .put("status",it.status).put("assignee",it.assignee ?: JSONObject.NULL).put("dueDate",it.dueDate ?: JSONObject.NULL)
                .put("createdAt",it.createdAt).put("updatedAt",it.updatedAt)) } })
            val bytes = payload.toString().toByteArray(); if (bytes.size > 8 * 1024 * 1024) continue
            val request = PutDataMapRequest.create("/onvexa/results/${row.id}")
            request.dataMap.putAsset("result",Asset.createFromBytes(bytes)); request.dataMap.putString("revision",digest(bytes))
            data.putDataItem(request.asPutDataRequest()).await()
        }
    }
    private suspend fun receiveResults(item: DataItem) {
        val payload = metadata(DataMapItem.fromDataItem(item).dataMap,"result")
        val id = payload.getString("recordingId"); require(validId(id))
        val row = db.recordingDao().getById(id) ?: return
        if (payload.optBoolean("deleted")) {
            db.recordingDao().update(row.copy(deletedAt = System.currentTimeMillis(),syncState = 0))
            val node = Wearable.getNodeClient(context).localNode.await().id
            data.deleteDataItems(Uri.parse("wear://$node/onvexa/recordings/$id")).await()
            return
        }
        val now = System.currentTimeMillis()
        db.withTransaction {
            if (row.title != payload.optString("title")) db.recordingDao().update(row.copy(title = payload.optString("title").ifBlank { null }))
            val summary = payload.optJSONObject("summary")
            if (summary != null && validId(summary.getString("id"))) db.summaryDao().upsertSummary(SummaryEntity(summary.getString("id"),id,null,
                summary.getString("executive"),summary.getString("detailed"),"[]","[]","[]",null,null,summary.getLong("createdAt"),summary.getLong("updatedAt"),syncState = 0))
            val existing = db.summaryDao().observeActionItems(id).first().associateBy { it.id }
            val actions = payload.optJSONArray("actions") ?: JSONArray()
            val received = (0 until actions.length()).map { index ->
                val a = actions.getJSONObject(index); val aid = a.getString("id"); require(validId(aid))
                val old = existing[aid]; val updated = a.getLong("updatedAt")
                if (old != null && old.syncState != 0 && old.updatedAt > updated) old else
                    ActionItemEntity(aid,id,null,a.getString("description"),a.optString("assignee").takeUnless { a.isNull("assignee") },
                        a.optString("dueDate").takeUnless { a.isNull("dueDate") },a.getString("status"),null,a.getLong("createdAt"),updated,syncState = 0)
            }
            db.summaryDao().upsertActionItems(received)
        }
    }
    private suspend fun publishActions() {
        val actions = db.summaryDao().observeActionRows().first()
        for (action in actions) {
            val row = db.summaryDao().observeActionItems(action.recordingId).first().firstOrNull { it.id == action.id && it.syncState != 0 } ?: continue
            val request = PutDataMapRequest.create("/onvexa/actions/${row.id}")
            request.dataMap.putString("id",row.id); request.dataMap.putString("recordingId",row.recordingId)
            request.dataMap.putString("status",row.status); request.dataMap.putLong("updatedAt",row.updatedAt)
            data.putDataItem(request.asPutDataRequest().setUrgent()).await()
        }
    }
    private suspend fun receiveAction(item: DataItem) {
        val map = DataMapItem.fromDataItem(item).dataMap
        val id = map.getString("id") ?: return; val recordingId = map.getString("recordingId") ?: return
        if (!validId(id) || !validId(recordingId) || db.recordingDao().getById(recordingId)?.source != "watch") return
        val action = db.summaryDao().observeActionItems(recordingId).first().firstOrNull { it.id == id } ?: return
        val state = map.getString("status") ?: return; if (state !in listOf("open","done","dismissed")) return
        if (action.status != state && map.getLong("updatedAt") >= action.updatedAt) {
            db.summaryDao().setActionStatus(id,state,System.currentTimeMillis())
            (context.applicationContext as? WearChangeListener)?.onWearChanged()
        }
    }
    private fun validId(id: String) = runCatching { MediaFiles.uuid(id) }.getOrDefault(false)
    private fun wasPurged(id: String) = db.query(SimpleSQLiteQuery("SELECT 1 FROM sync_metadata WHERE `key`=?",arrayOf("purged:recordings:$id"))).use { it.moveToFirst() }
    private suspend fun publishDeletion(id: String) {
        if (!validId(id)) return
        val request = PutDataMapRequest.create("/onvexa/results/$id")
        request.dataMap.putAsset("result",Asset.createFromBytes(JSONObject().put("recordingId",id).put("deleted",true).toString().toByteArray()))
        data.putDataItem(request.asPutDataRequest().setUrgent()).await()
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}

@EntryPoint @InstallIn(SingletonComponent::class)
interface WearTransferEntryPoint { fun transfer(): WearTransfer }
class WearTransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext,WearTransferEntryPoint::class.java).transfer().synchronize(); Result.success()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
}
class WearTransferListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        if (events.any { it.type == DataEvent.TYPE_CHANGED }) EntryPointAccessors.fromApplication(applicationContext,WearTransferEntryPoint::class.java).transfer().schedule()
    }
    override fun onPeerConnected(peer: Node) { EntryPointAccessors.fromApplication(applicationContext,WearTransferEntryPoint::class.java).transfer().schedule() }
}
