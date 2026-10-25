package com.onvexa.recorder.app.backend

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.onvexa.recorder.core.database.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SyncLocalStoreTest {
    @Test fun purgedRecordingRemovesChildrenAndKeepsDeletionMarker() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val local = SyncLocalStore(db); val row = recording("2026-01-01T00:00:01Z")
            local.applyRemote("recordings",row,local.columns("recordings"))
            val id = row.getValue("id").jsonPrimitive.content
            db.noteDao().upsert(com.onvexa.recorder.core.database.entity.NoteEntity("note",id,0,"pending old note",0,0))
            local.applyPurge("recordings",id)
            local.applyPurge("recordings",id)
            assertNull(db.recordingDao().getById(id))
            assertTrue(local.rows("notes").isEmpty())
            assertEquals("1",local.metadata("purged:recordings:$id"))
        } finally { db.close() }
    }
    @Test fun cloudPullCannotReplaceAnOpenMicrophoneRecording() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val local = SyncLocalStore(db); val row = recording("2026-01-01T00:00:01Z")
            local.applyRemote("recordings",row,local.columns("recordings"))
            val id=row.getValue("id").jsonPrimitive.content
            db.recordingDao().setStatus(id,"recording",50)
            local.applyRemote("recordings",recording("2026-01-01T00:00:02Z","stale remote"),local.columns("recordings"))
            assertEquals("recording",db.recordingDao().getById(id)!!.status)
            assertEquals("Title",db.recordingDao().getById(id)!!.title)
        } finally { db.close() }
    }
    private fun recording(version: String, title: String = "Title") = buildJsonObject {
        put("id", "11111111-1111-4111-8111-111111111111"); put("user_id", "user")
        put("source","phone"); put("status","recorded"); put("title",title); put("duration_ms",1000)
        put("started_at","2026-01-01T00:00:00Z"); put("created_at","2026-01-01T00:00:00Z")
        put("updated_at",version)
    }
    @Test fun replayDoesNotOverwritePendingEditAndNewRemoteVersionWins() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val local = SyncLocalStore(db)
            val columns = local.columns("recordings")
            val remote = recording("2026-01-01T00:00:01.000001Z")
            local.applyRemote("recordings",remote,columns)
            val id = remote.getValue("id").jsonPrimitive.content
            db.recordingDao().rename(id,"Local edit", 20)
            local.applyRemote("recordings",remote,columns)
            assertEquals("Local edit",db.recordingDao().getById(id)!!.title)
            assertEquals(1,db.recordingDao().getById(id)!!.syncState)
            local.applyRemote("recordings",recording("2026-01-01T00:00:01.000002Z","Remote edit"),columns)
            assertEquals("Remote edit",db.recordingDao().getById(id)!!.title)
            assertEquals(0,db.recordingDao().getById(id)!!.syncState)
        } finally { db.close() }
    }
    @Test fun acknowledgementDoesNotDropChangesMadeWhileRequestWasInFlight() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val local = SyncLocalStore(db)
            val remote = recording("2026-01-01T00:00:01Z")
            local.applyRemote("recordings",remote,local.columns("recordings"))
            val id = remote.getValue("id").jsonPrimitive.content
            db.recordingDao().rename(id,"First edit",20)
            val snapshot = local.rows("recordings",pending=true).single()
            db.recordingDao().rename(id,"Second edit",21)
            local.acknowledge("recordings",snapshot,recording("2026-01-01T00:00:02Z","First edit"))
            assertEquals("Second edit",db.recordingDao().getById(id)!!.title)
            assertEquals(1,db.recordingDao().getById(id)!!.syncState)
        } finally { db.close() }
    }
    @Test fun remoteUpsertPreservesChildrenInsteadOfReplacingParent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val local = SyncLocalStore(db)
            val remote = recording("2026-01-01T00:00:01Z")
            local.applyRemote("recordings",remote,local.columns("recordings"))
            val id = remote.getValue("id").jsonPrimitive.content
            db.noteDao().upsert(com.onvexa.recorder.core.database.entity.NoteEntity("note", id, 0,"keep",0,0))
            local.applyRemote("recordings",recording("2026-01-01T00:00:02Z"),local.columns("recordings"))
            assertEquals(1,local.rows("notes").size)
        } finally { db.close() }
    }
}
