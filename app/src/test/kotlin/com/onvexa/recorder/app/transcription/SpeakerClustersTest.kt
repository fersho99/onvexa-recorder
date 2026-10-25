package com.onvexa.recorder.app.transcription

import org.junit.Assert.*
import org.junit.Test

class SpeakerClustersTest {
    @Test fun repeatedVoiceKeepsClusterDespiteVolume() {
        val clusters = SpeakerClusters()
        assertEquals(0,clusters.assign(doubleArrayOf(1.0,0.0),100))
        assertEquals(0,clusters.assign(doubleArrayOf(5.0,0.1),100))
        assertEquals(1,clusters.assign(doubleArrayOf(0.0,1.0),100))
        assertEquals(0,clusters.assign(doubleArrayOf(0.9,0.0),100))
    }
    @Test fun uncertainOrMalformedEmbeddingsRemainUnknown() {
        val clusters = SpeakerClusters()
        assertNull(clusters.assign(doubleArrayOf(1.0,0.0),20))
        assertNull(clusters.assign(doubleArrayOf(Double.NaN),100))
        assertNull(clusters.assign(doubleArrayOf(0.0,0.0),100))
        clusters.assign(doubleArrayOf(1.0,0.0),100)
        assertNull(clusters.assign(doubleArrayOf(1.0),100))
    }
}
