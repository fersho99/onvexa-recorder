package com.onvexa.recorder.core.audio

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CaptureClockTest {
    @Test fun pausesAreNotIncludedAndRepeatedCommandsAreIdempotent() {
        val clock = CaptureClock()
        clock.resume(100); clock.resume(150)
        assertEquals(200L, clock.elapsed(300))
        clock.pause(300); clock.pause(700)
        assertEquals(200L, clock.elapsed(900))
        clock.resume(1000); clock.pause(1300)
        assertEquals(500L, clock.elapsed(2000))
    }
    @Test fun pathsCannotEscapePrivateStorage() {
        val root = Files.createTempDirectory("onvexa-test").toFile()
        try {
            assertEquals(java.io.File(root,"a.m4a").canonicalFile, MediaFiles.privateFile(root, java.io.File(root,"a.m4a").path))
            try { MediaFiles.privateFile(root, java.io.File(root,"../outside").path); fail() } catch (_: IllegalArgumentException) { }
        } finally { root.delete() }
    }
}
