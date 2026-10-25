package com.onvexa.recorder.app.ui.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File

/** La camara externa recibe permiso solo para un archivo temporal, nunca acceso al audio. */
@Composable
fun PhotoCaptureButton(enabled: Boolean = true, onImage: (Uri) -> Unit) {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        pending?.let { if (saved) onImage(Uri.parse(it)) }
        pending = null
    }
    TextButton(enabled = enabled,onClick = {
        try {
            val folder = File(context.cacheDir,"camera").apply { mkdirs() }
            val file = File.createTempFile("photo-",".jpg",folder)
            val uri = FileProvider.getUriForFile(context,context.packageName + ".photos",file)
            pending = uri.toString(); failed = false; capture.launch(uri)
        } catch (_: Exception) { failed = true }
    }) { Text(if (failed) "Cámara no disponible" else "Tomar foto") }
}
