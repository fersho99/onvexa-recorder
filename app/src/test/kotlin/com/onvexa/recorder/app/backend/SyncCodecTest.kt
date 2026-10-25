package com.onvexa.recorder.app.backend

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SyncCodecTest {
    @Test fun encodeConvertsTimesAndJsonWithoutSendingLocalFields() {
        val local = buildJsonObject {
            put("id", "x"); put("createdAt", 1000); put("updatedAt", 2000)
            put("localAudioPath", "/private/file"); put("remoteAudioPath", "user/file.opus")
            put("syncState", 1); put("keyTopicsJson", "[\"tema\"]"); put("deletedAt", JsonNull)
        }
        val remote = SyncCodec.encode(local, "user")
        assertEquals("1970-01-01T00:00:01Z", remote.getValue("created_at").jsonPrimitive.content)
        assertTrue(remote["key_topics"] is JsonArray)
        assertFalse("updated_at" in remote)
        assertFalse("local_audio_path" in remote)
        assertFalse("sync_state" in remote)
        assertEquals("user/file.opus", remote.getValue("audio_path").jsonPrimitive.content)
    }
    @Test fun decodePreservesLocalAudioAndConvertsTombstone() {
        val remote = buildJsonObject {
            put("id", "x"); put("updated_at", "2026-01-01T00:00:00.123456Z")
            put("deleted_at", "2026-01-01T00:00:00Z"); put("audio_path", "u/r.opus")
        }
        val old = buildJsonObject { put("localAudioPath", "/local/audio") }
        val decoded = SyncCodec.decode(remote, listOf("id","updatedAt","deletedAt","localAudioPath","remoteAudioPath","syncState","remoteUpdatedAt"), old)
        assertEquals("/local/audio", decoded.getValue("localAudioPath").jsonPrimitive.content)
        assertEquals(0, decoded.getValue("syncState").jsonPrimitive.int)
        assertTrue(decoded.getValue("deletedAt").jsonPrimitive.long > 0)
        assertEquals(decoded["updatedAt"], decoded["remoteUpdatedAt"])
    }
    @Test fun relationUsesCompositeKeyAndDemosStayLocal() {
        val row = buildJsonObject { put("recordingId","demo-recording"); put("tagId","tag") }
        assertEquals(listOf("recordingId","tagId"), SyncCodec.keys("recording_tags"))
        assertEquals("demo-recording:tag", SyncCodec.key("recording_tags",row))
        assertTrue(SyncCodec.isDemo("recording_tags",row))
    }
}
