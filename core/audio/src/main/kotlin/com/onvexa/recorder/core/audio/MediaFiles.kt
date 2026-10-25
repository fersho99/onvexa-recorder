package com.onvexa.recorder.core.audio

import java.io.File
import java.security.MessageDigest

object MediaFiles {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun privateFile(root: File, path: String): File {
        val target = File(path).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator)) { "file_outside_private_storage" }
        return target
    }
    fun uuid(value: String) = java.util.UUID.fromString(value).toString() == value.lowercase()
}
